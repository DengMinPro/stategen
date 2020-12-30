/*
 * Copyright (C) 2018  niaoge<78493244@qq.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.stategen.framework.generator.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.stategen.framework.lite.CaseInsensitiveHashMap;
import org.stategen.framework.util.CollectionUtil;
import org.stategen.framework.util.Context;
import org.stategen.framework.util.PropUtil;
import org.stategen.framework.util.StringUtil;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;

import cn.org.rapid_framework.generator.GenUtils;
import cn.org.rapid_framework.generator.GeneratorProperties;
import cn.org.rapid_framework.generator.provider.db.sql.model.Sql;
import cn.org.rapid_framework.generator.provider.db.sql.model.SqlParameter;
import cn.org.rapid_framework.generator.provider.db.table.model.Column;
import cn.org.rapid_framework.generator.provider.db.table.model.Table;
import cn.org.rapid_framework.generator.util.GLogger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.Cleanup;

/**
 * The Class<?> FmHelper.
 */
@SuppressWarnings("all")
public class FmHelper {
    
    final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FmHelper.class);
    

    public static void readTxtFile(String filePath) {
        try {
            File file = new File(filePath);
            //判断文件是否存在
            if (file.isFile() && file.exists()) {
                //考虑到编码格式
                @Cleanup
                FileInputStream   fileInputStream = new FileInputStream(file);
                @Cleanup
                InputStreamReader read            = new InputStreamReader(fileInputStream, StringUtil.UTF_8);
                @Cleanup
                BufferedReader    bufferedReader  = new BufferedReader(read);
                String            lineTxt         = null;
                while ((lineTxt = bufferedReader.readLine()) != null) {
                    GLogger.info(lineTxt);
                }
            } else {
                GLogger.error("找不到指定的文件");
            }
        } catch (Exception e) {
            GLogger.error("读取文件内容出错", e);
        }
    }
    
    protected static void changeCaseColumnName(LinkedHashSet<Column> columns, Map<String, String> oldFieldMap) {
        if (CollectionUtil.isEmpty(columns)) {
            return;
        }
        
        Map<String, Column> newFieldMap = new CaseInsensitiveHashMap<Column>();
        
        for (Column column : columns) {
            String destColumnName = column.getColumnName();
            newFieldMap.put(destColumnName, column);
        }
        
        //替换collumnName的大小写
        if (CollectionUtil.isNotEmpty(oldFieldMap)) {
            for (Entry<String, String> entry : oldFieldMap.entrySet()) {
                String newColumnName = entry.getValue();
                Column column        = newFieldMap.get(newColumnName);
                if (column != null) {
                    column.setColumnName(newColumnName);
                }
            }
        }
    }
    
    /**
     * Process template.
     *
     * @param template the template
     * @param model the model
     * @param outputFile the output file
     * @param encoding the encoding
     * @throws Exception 
     */
    public static void processTemplate(
            Template template,
            Map<String, Object> model,
            File outputFile,
            String encoding,
            boolean isTable,
            boolean hasAtNotRelace,
            JavaType javaType ) throws Exception {
        
        //可以同时用来判断是否需要做java文件
        CompilationUnit lastCompilationUnit = null;
        String          outFileName         = outputFile.getName();
        File            canonicalFile;
        try {
            canonicalFile = outputFile.getCanonicalFile();
        } catch (Exception e1) {
            GLogger.error(
                    new StringBuilder("如果参数内设置 add_illegal_prefix 那么生成的 className 将有一个 '?'字符以阻止dal层生成,目的是让你先检测类名是否符合要求,\n")
                            .append("比如驼峰写法，比如可以避免windows不区分文件名的大小写的麻烦,\n请先检查tables内相应的xml文件:\n")
                            .append(e1.getMessage())
                            .append(" \n")
                            .toString(),
                    e1);
            throw e1;
        }
        boolean notReplacefile = "false".equals(GeneratorProperties.getProperties().get("replace_file"));
        notReplacefile = notReplacefile || hasAtNotRelace;
        boolean isFileExits = false;
        if (canonicalFile != null && canonicalFile.exists() && canonicalFile.isFile()) {
            isFileExits = true;
            if (notReplacefile) {
                GLogger.info(">>>>>>>>>>>>>文件已存在，不再次生成:" + canonicalFile.getCanonicalFile());
                @Cleanup
                CharArrayWriter charArrayWriter = new CharArrayWriter(1024 * 1024);
                template.process(model, charArrayWriter);
                GLogger.info("" + charArrayWriter);
                return;
            }
            //如果文件存在，并且是DTO.java,将不覆盖
            if (outFileName.endsWith(PropUtil.getDTOSuffixJava())) {
                GLogger.info("DTO文件已存在，将不会覆盖:" + canonicalFile);
                return;
            }
            if (javaType !=null) {
            //if (outFileName.endsWith(".java") && !outFileName.endsWith("SqlDaoSupportBase.java")) {
            //    javaType = getJavaType(outFileName);
                GLogger.info(javaType + "<===========>:" + outFileName);
                lastCompilationUnit = parserJava2Unit(canonicalFile);
            }
        }
        
        @Cleanup
        CharArrayWriter charArrayWriter = new CharArrayWriter(1024 * 1024);
        boolean         newFileError    = false;
        if (JavaType.isEntry.equals(javaType)) {
            if (lastCompilationUnit != null && notReplacefile) {
                return;
            }
            
            Map<String, String> oldFieldMap = ASTHelper.getFieldMap(lastCompilationUnit);
            CompatibleHelper.OLD_FIELDS.clear();
            if (CollectionUtil.isNotEmpty(oldFieldMap)) {
                CompatibleHelper.OLD_FIELDS.putAll(oldFieldMap);
            }
            
            //替换为pojo的大小写格式
            Table                 table   = GenUtils.globalTableConfig.getTable();
            LinkedHashSet<Column> columns = table.getColumns();
            changeCaseColumnName(columns, oldFieldMap);
            
            List<Sql> sqls = GenUtils.globalTableConfig.getSqls();
            //替换参数的大小写
            if (CollectionUtil.isNotEmpty(sqls)) {
                for (Sql sql : sqls) {
                    changeCaseColumnName(sql.getColumns(), oldFieldMap);
                    LinkedHashSet<SqlParameter> params = sql.getParams();
                    
                    if (CollectionUtil.isNotEmpty(params)) {
                        for (SqlParameter sqlParameter : params) {
                            String destColumnName = sqlParameter.getParamName();
                            String newColumnName  = oldFieldMap.get(destColumnName);
                            if (newColumnName != null) {
                                sqlParameter.setParamName(newColumnName);
                            }
                        }
                    }
                }
            }
        }
        
        //如果是java文件,将与原来的java文件对比，如果有旧的方法及属性将保留
        try {
            template.process(model, charArrayWriter);
            if (lastCompilationUnit != null) {
                ASTHelper.replaceJava(lastCompilationUnit, javaType, charArrayWriter, canonicalFile.getName());
            }
        } catch (ParseException e) {
            GLogger.error("将要生成的文件: " + canonicalFile.getName() + "........有个解析错误，文件将不会被覆盖，除非你删除或修复该文件" + "\n"
                    + charArrayWriter.toString(),
                    e);
            newFileError = true;
            return;
        }
        
        
        if (lastCompilationUnit == null || !newFileError) {
            //如果是table xml文件，如果文件已存在
            
            if (isTable && isFileExits && StringUtil.endsWithIgnoreCase(outFileName, ".xml")) {
                GLogger.info("====>table文件已存在，将不会覆盖，下面是table的xml文件，如果需要，请自行拷贝,\n" + canonicalFile);
                GLogger.info(charArrayWriter.toString());
                return;
            }
            
            String gerneratedText = checkAndConvertIfMybatisXml(javaType, charArrayWriter, isFileExits);
            if (isFileExits) {
                String oldFileText = IOHelpers.readFile(canonicalFile, StringUtil.UTF_8, false);
                if (oldFileText.equals(gerneratedText)) {
                    oldFileText = null;
                    GLogger.info("智能忽略重写入----------------->:" + canonicalFile.getName());
                    return;
                }
                oldFileText = null;
            }
            @Cleanup
            FileOutputStream fileOutputStream = new FileOutputStream(canonicalFile);
            
            @Cleanup
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, encoding);
            
            @Cleanup
            BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
            charArrayWriter.writeTo(bufferedWriter);
            
            gerneratedText = null;
            
        }
        
    }

    private static CompilationUnit parserJava2Unit(File canonicalFile)  {
        CompilationUnit javaUnit =null;
        try {
            javaUnit = JavaParser.parse(canonicalFile, Charset.forName(StringUtil.UTF_8));
        } catch (Exception e) {
            String javaFileText;
            try {
                javaFileText = IOHelpers.readFile(canonicalFile, StringUtil.UTF_8, true);
            } catch (IOException e1) {
                throw new IllegalArgumentException("read file error"+canonicalFile,e);
            }
            GLogger.error(
                    new StringBuilder("java文件:").append(canonicalFile).append(" \n").append(javaFileText).toString(),
                    e);
            throw new IllegalArgumentException("parser file error:"+canonicalFile,e);
        }
        return javaUnit;
    }
    
    private static String checkAndConvertIfMybatisXml(
            JavaType javaType,
            CharArrayWriter charArrayWriter,
            boolean isFileExits) throws IOException {
        String gerneratedText = null;
        if (javaType == null) {
            if (GenProperties.daoType == DaoType.mybatis) {
                gerneratedText = charArrayWriter.toString();
                if (gerneratedText.lastIndexOf("</sqlMap>") > 0) {
                    gerneratedText = IbatisXmlToMybatis.transformXmlByXslt(gerneratedText);
                    charArrayWriter.reset();
                    charArrayWriter.write(gerneratedText);
                }
                return gerneratedText;
            }
        }
        
        if (isFileExits) {
            gerneratedText = charArrayWriter.toString();
        }
        return gerneratedText;
    }
    
    /**
     * Process template string.
     *
     * @param templateString the template string
     * @param model the model
     * @param conf the conf
     * @return the string
     */
    public static String processTemplateString(String templateString, Map<String, Object> model, Configuration conf) {
        try {
            @Cleanup
            StringWriter out      = new StringWriter();
            Template     template = new Template(templateString, new StringReader(templateString), conf);
            template.process(model, out);
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("cannot process templateString:" + templateString + " cause:" + e, e);
        }
    }
    
    public static void parserDirAllJavaInterfaceMethodCount(File javaFile)  {
        GLogger.info("absoluteOutputFilePath<===========>:" + javaFile.getParent());
        File dir  =new File(javaFile.getParent());
        File[] listFiles = dir.listFiles(  (File pathname) ->{
            return !pathname.isDirectory() && pathname.getName().endsWith(".java");
        });
        
        if (CollectionUtil.isNotEmpty(listFiles)) {
            for (File file : listFiles) {
                CompilationUnit compilationUnit = parserJava2Unit(file);
                Integer unitMethodCount = ASTHelper.getUnitMethodCount(compilationUnit);
                Context.FACADE_SERIVCES_METHOD_COUNT.put(file.getName(), unitMethodCount);
            }
        }
    }
}
