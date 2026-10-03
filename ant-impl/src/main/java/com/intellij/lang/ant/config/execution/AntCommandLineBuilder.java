/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.execution;

import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import consulo.apache.ant.impl.localize.ApacheAntImplLocalize;
import consulo.apache.ant.rt.common.AntLoggerConstants;
import consulo.component.util.config.AbstractProperty;
import consulo.container.plugin.PluginManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.execution.CantRunException;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.pathMacro.Macro;
import consulo.pathMacro.MacroManager;
import consulo.process.cmd.ParametersList;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.ClassPathUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AntCommandLineBuilder {
    private final List<String> myTargets = new ArrayList<>();
    private final OwnJavaParameters myCommandLine = new OwnJavaParameters();
    private String myBuildFilePath;
    private List<BuildFileProperty> myProperties;
    private boolean myDone = false;
    private final List<String> myExpandedProperties = new ArrayList<>();
    private static final String INPUT_HANDLER_PARAMETER = "-inputhandler";
    private static final String LOGFILE_PARAMETER = "-logfile";
    private static final String LOGFILE_SHORT_PARAMETER = "-l";

    public void calculateProperties(DataContext dataContext, List<BuildFileProperty> additionalProperties)
        throws Macro.ExecutionCancelledException {
        for (BuildFileProperty property : myProperties) {
            expandProperty(dataContext, property);
        }
        for (BuildFileProperty property : additionalProperties) {
            expandProperty(dataContext, property);
        }
    }

    private void expandProperty(DataContext dataContext, BuildFileProperty property) throws Macro.ExecutionCancelledException {
        String value = property.getPropertyValue();
        MacroManager macroManager = GlobalAntConfiguration.getMacroManager();
        value = macroManager.expandMacrosInString(value, true, dataContext);
        value = macroManager.expandMacrosInString(value, false, dataContext);
        myExpandedProperties.add("-D" + property.getPropertyName() + "=" + value);
    }

    public void addTarget(String targetName) {
        myTargets.add(targetName);
    }

    public void setBuildFile(AbstractProperty.AbstractPropertyContainer container, File buildFile) throws CantRunException {
        String jdkName = AntBuildFileImpl.CUSTOM_JDK_NAME.get(container);
        Sdk jdk;
        if (jdkName != null && jdkName.length() > 0) {
            jdk = GlobalAntConfiguration.findJdk(jdkName);
        }
        else {
            jdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(container);
            if (jdkName == null || jdkName.length() == 0) {
                throw new CantRunException(ApacheAntImplLocalize.projectJdkNotSpecifiedErrorMessage());
            }
            jdk = GlobalAntConfiguration.findJdk(jdkName);
        }
        if (jdk == null) {
            throw new CantRunException(ApacheAntImplLocalize.jdkWithNameNotConfiguredErrorMessage(jdkName));
        }
        VirtualFile homeDirectory = jdk.getHomeDirectory();
        if (homeDirectory == null) {
            throw new CantRunException(ApacheAntImplLocalize.jdkWithNameBadConfiguredErrorMessage(jdkName));
        }
        myCommandLine.setJdk(jdk);

        ParametersList vmParametersList = myCommandLine.getVMParametersList();
        vmParametersList.add("-Xmx" + AntBuildFileImpl.MAX_HEAP_SIZE.get(container) + "m");
        vmParametersList.add("-Xss" + AntBuildFileImpl.MAX_STACK_SIZE.get(container) + "m");

        Sdk antInstallation = AntBuildFileImpl.ANT_INSTALLATION.get(container);
        if (antInstallation == null) {
            throw new CantRunException(ApacheAntImplLocalize.antInstallationNotConfiguredErrorMessage());
        }

        String antHome = antInstallation.getHomePath();
        vmParametersList.add("-Dant.home=" + antHome);
        String libraryDir = antHome + (antHome.endsWith("/") || antHome.endsWith(File.separator) ? "" : File.separator) + "lib";
        vmParametersList.add("-Dant.library.dir=" + libraryDir);

        String[] urls = jdk.getRootProvider().getUrls(BinariesOrderRootType.ID);
        String jdkHome = homeDirectory.getPath().replace('/', File.separatorChar);
        String pathToJre = jdkHome + File.separator + "jre" + File.separator;
        for (String url : urls) {
            String path = VirtualFilePathUtil.toPresentableUrl(url);
            if (!path.startsWith(pathToJre)) {
                myCommandLine.getClassPath().add(path);
            }
        }

        myCommandLine.getClassPath().addAllFiles(AntBuildFileImpl.ALL_CLASS_PATH.get(container));

        File pluginPath = PluginManager.getPluginPath(AntCommandLineBuilder.class);

        myCommandLine.getClassPath().addAllFiles(AntBuildFileImpl.getUserHomeLibraries());
        // hardcoded since it's not loaded by classloader
        myCommandLine.getClassPath().add(new File(pluginPath, "ant-rt.jar"));
        myCommandLine.getClassPath().add(ClassPathUtil.getJarPathForClass(AntLoggerConstants.class));
        myCommandLine.getClassPath().add(ClassPathUtil.getJarPathForClass(ServiceMessage.class));

        if (jdk.getSdkType() instanceof JavaSdkType jdkType) {
            String toolsJar = jdkType.getToolsPath(jdk);
            if (toolsJar != null) {
                myCommandLine.getClassPath().add(toolsJar);
            }
        }
        JavaSdkUtil.addRtJar(myCommandLine.getClassPath());

        myCommandLine.setMainClass("com.intellij.rt.ant.execution.AntMain2");
        ParametersList programParameters = myCommandLine.getProgramParametersList();

        String additionalParams = AntBuildFileImpl.ANT_COMMAND_LINE_PARAMETERS.get(container);
        if (additionalParams != null) {
            for (String param : ParametersList.parse(additionalParams)) {
                if (param.startsWith("-J")) {
                    String cutParam = param.substring("-J".length());
                    if (cutParam.length() > 0) {
                        vmParametersList.add(cutParam);
                    }
                }
                else {
                    programParameters.add(param);
                }
            }
        }

        if (!(programParameters.getList().contains(LOGFILE_SHORT_PARAMETER) || programParameters.getList().contains(LOGFILE_PARAMETER))) {
            //programParameters.add("-logger", "com.intellij.rt.ant.execution.IdeaAntLogger2");
            programParameters.add("-logger", "consulo.apache.ant.rt.ConsuloAntLogger");
        }
        if (!programParameters.getList().contains(INPUT_HANDLER_PARAMETER)) {
            programParameters.add(INPUT_HANDLER_PARAMETER, "com.intellij.rt.ant.execution.IdeaInputHandler");
        }

        myProperties = AntBuildFileImpl.ANT_PROPERTIES.get(container);

        myBuildFilePath = buildFile.getAbsolutePath();
        myCommandLine.setWorkingDirectory(buildFile.getParent());
    }

    public OwnJavaParameters getJavaParameters() {
        if (myDone) {
            return myCommandLine;
        }
        ParametersList programParameters = myCommandLine.getProgramParametersList();
        for (String property : myExpandedProperties) {
            if (property != null) {
                programParameters.add(property);
            }
        }
        programParameters.add("-buildfile", myBuildFilePath);
        for (String target : myTargets) {
            if (target != null) {
                programParameters.add(target);
            }
        }
        myDone = true;
        return myCommandLine;
    }

    public void addTargets(String[] targets) {
        ContainerUtil.addAll(myTargets, targets);
    }

    public String[] getTargets() {
        return ArrayUtil.toStringArray(myTargets);
    }
}
