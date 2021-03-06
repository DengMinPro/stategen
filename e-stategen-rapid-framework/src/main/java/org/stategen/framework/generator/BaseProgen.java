/*
 * Copyright (C) 2018 niaoge<78493244@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.stategen.framework.generator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stategen.framework.generator.util.FileHelpers;
import org.stategen.framework.generator.util.GenNames;
import org.stategen.framework.generator.util.GenProperties;
import org.stategen.framework.generator.util.IOHelpers;
import org.stategen.framework.generator.util.TemplateHelpers;
import org.stategen.framework.progen.FacadeGenerator;
import org.stategen.framework.util.AssertUtil;
import org.stategen.framework.util.StringUtil;
import org.stategen.framework.util.XmlUtil;

import cn.org.rapid_framework.generator.util.StringHelper;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

public class BaseProgen {

    final static Logger logger = LoggerFactory.getLogger(BaseProgen.class);

    static String   APPEND_TAG_DO_NOT_CHANGE = "<!--APPEND_TAG_DO_NOT_CHANGE-->";
    static String   APPEND_TAG_DO_NOT_CHANGE_REG = "\\s*<\\!--\\s*APPEND_TAG_DO_NOT_CHANGE\\s*-->";

    static String[] springbootForReplaces    = { "springboot_import", "springboot_dependencies", "springboot_plugin" };

    static String springboot_import       = "<!-- springboot_import -->";

    static String springboot_dependencies = "<!-- springboot_dependencies -->";

    static String springboot_plugin       = "<!-- springboot_plugin --> ";

    static String ROLE ="role";

    public BaseProgen(Object global) {

    }


    protected Properties getRootProperties() throws IOException {
        String     genConfigXmlIfRunTest = GenProperties.getGenConfigXmlIfRunTest();
        Properties root                  = GenProperties.getAllMergedProps(genConfigXmlIfRunTest);
        root.put("generator_tools_class", "org.stategen.framework.util.StringUtil");
        String demo = root.getProperty(ROLE);
        root.put(ROLE, StringUtil.equals("true", demo));

        GenProperties.systemName  = root.getProperty(GenNames.systemName);
        GenProperties.packageName = root.getProperty(GenNames.packageName);
        GenProperties.cmdPath     = root.getProperty(GenNames.cmdPath);

        return root;
    }

    private String processTempleteFiles(Properties root, String tempPath) throws TemplateException, IOException {

        Configuration conf           = TemplateHelpers.getConfiguration(tempPath);
        File          tempFolderFile = FileHelpers.getFile(tempPath);
        conf.setDirectoryForTemplateLoading(tempFolderFile);
        List<File>   allFiles    = FileHelpers.searchAllNotIgnoreFile(tempFolderFile);
        String       projectName = null;
        int          folderCount = 0;
        List<String> outFiles    = new ArrayList<String>();
        for (File ftlFile : allFiles) {
            String relativeFileName = FileHelpers.getRelativeFileName(tempFolderFile, ftlFile);
            if (ftlFile.isFile()) {
                FacadeGenerator.processTemplate(ftlFile, conf, root, GenProperties.getProjectsPath(), relativeFileName,
                        outFiles);
            } else {
                String targetFileName = TemplateHelpers.processTemplitePath(root, relativeFileName);
                String filePath       = StringUtil.concatPath(GenProperties.getProjectsPath(), targetFileName) + '/';
                filePath = FileHelpers.replaceUnOverridePath(filePath);
                FileHelpers.parentMkdir(filePath);
                folderCount++;
                if (projectName == null && folderCount == 2) {
                    projectName = targetFileName;
                }
            }
        }
        return projectName;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void system() throws IOException, TemplateException {
        Properties root = getRootProperties();
        root.putAll(StringHelper.getDirValuesMap((Map)root));

        //system 映射到 system
        String projectsTempPath = FileHelpers.getCanonicalPath(GenProperties.dir_templates_root + "/java/system@/");
        processTempleteFiles(root, projectsTempPath);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void project() throws IOException, TemplateException, DocumentException {
        Properties root = getRootProperties();
        GenProperties.projectName = System.getProperty("projectName");

        putAppName(root,GenProperties.projectName);

        root.putAll(StringHelper.getDirValuesMap((Map)root));

        Boolean hasClient = false;
        String  webType   = System.getProperty("webType");
        if (StringUtil.isNotBlank(webType) && !"-e".equals(webType)) {
            hasClient = true;
            root.put("webType", webType);
        } else {
            root.put("webType", "");
        }
        root.put("hasClient", hasClient);

        String projectTempPath   = FileHelpers.getCanonicalPath(GenProperties.dir_templates_root + "/java/project@/");
        String projectFolderName = processTempleteFiles(root, projectTempPath);

        String projectsPomXmlFilename = StringUtil.concatPath(GenProperties.getProjectsPath(), "pom.xml");
        //dom4j格式化输出把换行等全部去掉，因此这里采用text输出
        String pomXmlText = XmlUtil.appendToNode(projectsPomXmlFilename, "module", projectFolderName);
        if (StringUtil.isNotEmpty(pomXmlText)) {
            IOHelpers.saveFile(new File(projectsPomXmlFilename), pomXmlText, StringUtil.UTF_8);
            if (logger.isInfoEnabled()) {
                logger.info(new StringBuilder("修改pom成功:").append(projectsPomXmlFilename).toString());
            }
        }
        if (hasClient) {
            processClient(root, hasClient, webType, projectFolderName);
        }

    }

    private void processClient(
            Properties root,
            Boolean hasClient,
            String webType,
            String projectFolderName) throws IOException, TemplateException, DocumentException {
        String webTypePath = FileHelpers
                .getCanonicalPath(GenProperties.dir_templates_root + "/java/frontend/" + webType + '/');
        File   webTypeFile = new File(webTypePath);
        AssertUtil.mustTrue(webTypeFile.exists(), webType + " 类型不存在 ,请输入 gen.sh -h 查看具体类型");
        String currentProjectPath = StringUtil.concatPath(GenProperties.getProjectsPath(), projectFolderName);

        String frontendName = GenProperties.projectName + "_frontend_" + webType;
        root.put("frontendName", frontendName);
        //向pom中增加插件
        String pomToReplaceFileName = StringUtil.concatPath(currentProjectPath, GenProperties.projectName + "-frontend-" + webType, "pomPluginText");

        File    pomToReplaceFile          = new File(pomToReplaceFileName);
        boolean pomToReplaceFileOldExists = pomToReplaceFile.exists() && pomToReplaceFile.isFile();
        processTempleteFiles(root, webTypePath);

        if (!pomToReplaceFileOldExists) {
            String mavenPluginExcutionText = IOHelpers.readFile(new File(pomToReplaceFileName), StringUtil.UTF_8);
            mavenPluginExcutionText ="\n" + mavenPluginExcutionText + "\n                            " + APPEND_TAG_DO_NOT_CHANGE;
            
            String projectPomXml  = StringUtil.concatPath(currentProjectPath, "pom.xml");
            String projectPomText = IOHelpers.readFile(new File(projectPomXml), StringUtil.UTF_8);
            projectPomText = projectPomText.replaceFirst(APPEND_TAG_DO_NOT_CHANGE_REG, mavenPluginExcutionText);
            IOHelpers.saveFile(new File(projectPomXml), projectPomText, StringUtil.UTF_8);
        }
    }


    protected String processProjectFolder(Properties root, String commandName) {
        String projectFolderName = StringUtil.trimLeftFormRightTo(GenProperties.cmdPath, StringUtil.SLASH);
        String projectFrefix     = "7-" + GenProperties.systemName + "-web-";
        AssertUtil.mustTrue(projectFolderName.startsWith(projectFrefix),
                commandName + " 命令必须在" + "7-" + GenProperties.systemName + "-web-xxx 执行");

        GenProperties.projectName = StringUtil.trimePrefixIgnoreCase(projectFolderName, projectFrefix);
        String projectName =GenProperties.projectName;
        root.put("projectName", projectName);

        putAppName(root, projectName);

        return projectFolderName;
    }

    private void putAppName(Properties root, String projectName) {
        String systemName = root.getProperty("systemName");
        String appName = StringUtil.capfirst(systemName)+StringUtil.capfirst(projectName);
        root.put("appName", appName);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void boot() throws IOException, TemplateException {
        AssertUtil.throwException("现在的StateGen就是一个Springboot+springcloud+alibaba项目，支持war和jar两种打包形式，不需要再转换");
        Properties root = getRootProperties();
        root.putAll(StringHelper.getDirValuesMap((Map)root));

        String projectFolderName = processProjectFolder(root, "boot");

        String springRootTempPath = FileHelpers
                .getCanonicalPath(GenProperties.dir_templates_root + "/java/spring-boot@/");
        String currentProjectPath = StringUtil.concatPath(GenProperties.getProjectsPath(), projectFolderName);
        processTempleteFiles(root, springRootTempPath);

        String projectPomXml  = StringUtil.concatPath(currentProjectPath, "pom.xml");
        String projectPomText = IOHelpers.readFile(new File(projectPomXml), StringUtil.UTF_8);
        for (String springBootForReplace : springbootForReplaces) {
            String spingBootReplaceFileName = StringUtil.concatPath(currentProjectPath, "src/main/java/config/",
                    springBootForReplace);
            String spingBootReplaceText     = IOHelpers.readFile(new File(spingBootReplaceFileName), StringUtil.UTF_8);

            projectPomText = projectPomText.replace("<!--" + springBootForReplace + "-->", spingBootReplaceText);
        }
        IOHelpers.saveFile(new File(projectPomXml), projectPomText, StringUtil.UTF_8);

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void client() throws IOException, TemplateException, DocumentException {
        Properties root = getRootProperties();
        root.putAll(StringHelper.getDirValuesMap((Map)root));
        String projectFolderName = processProjectFolder(root, "client");

        Boolean hasClient = false;
        String  webType   = System.getProperty("webType");
        if (StringUtil.isNotBlank(webType) && !"-e".equals(webType)) {
            hasClient = true;
            root.put("webType", webType);
        } else {
            root.put("webType", "");
        }
        root.put("hasClient", hasClient);

        if (hasClient) {
            processClient(root, hasClient, webType, projectFolderName);
        }
    }

}
