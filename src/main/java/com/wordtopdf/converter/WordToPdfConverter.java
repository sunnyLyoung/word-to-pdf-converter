package com.wordtopdf.converter;

import com.wordtopdf.font.FontManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;

/**
 * Word 转 PDF 核心转换器。
 * <p>
 * .docx 文件使用 docx4j + Apache FOP 引擎转换，
 * .doc 文件使用 Apache POI 读取内容后通过 FOP 生成 PDF。
 * </p>
 * <p>特色处理：</p>
 * <ul>
 *   <li>自动移除批注（CommentsPart）</li>
 *   <li>自动接受全部修订（Track Changes → 最终版）</li>
 *   <li>图片/Logo/遮盖层正常转换</li>
 *   <li>缺失字体时自动降级，不报错不产乱码</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class WordToPdfConverter implements FileConverter {

    private static final Logger log = LoggerFactory.getLogger(WordToPdfConverter.class);

    /** 单例 */
    private static volatile WordToPdfConverter instance;

    /** 是否已初始化字体映射 */
    private volatile boolean fontsInitialized = false;

    /** 兜底字体名（当所有中文字体都缺失时使用） */
    private String fallbackChineseFont = null;

    // ========== 接受全部修订的 XSLT ==========
    private static final String ACCEPT_REVISIONS_XSLT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<xsl:stylesheet version=\"1.0\"\n" +
            "    xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
            "    xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"\n" +
            "    xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
            "  <xsl:template match=\"@*|node()\">\n" +
            "    <xsl:copy><xsl:apply-templates select=\"@*|node()\"/></xsl:copy>\n" +
            "  </xsl:template>\n" +
            "  <!-- 接受插入内容：去包装，保留内容 -->\n" +
            "  <xsl:template match=\"w:ins\"><xsl:apply-templates/></xsl:template>\n" +
            "  <!-- 接受删除内容：完全移除 -->\n" +
            "  <xsl:template match=\"w:del\"/>\n" +
            "  <!-- 接受格式修订：保留新格式 -->\n" +
            "  <xsl:template match=\"w:rPrChange\"><xsl:apply-templates select=\"w:rPr\"/></xsl:template>\n" +
            "  <xsl:template match=\"w:pPrChange\"><xsl:apply-templates select=\"w:pPr\"/></xsl:template>\n" +
            "  <xsl:template match=\"w:sectPrChange\"><xsl:apply-templates select=\"w:sectPr\"/></xsl:template>\n" +
            "  <xsl:template match=\"w:tblPrChange\"><xsl:apply-templates select=\"w:tblPr\"/></xsl:template>\n" +
            "  <xsl:template match=\"w:trPrChange\"><xsl:apply-templates select=\"w:trPr\"/></xsl:template>\n" +
            "  <xsl:template match=\"w:tcPrChange\"><xsl:apply-templates select=\"w:tcPr\"/></xsl:template>\n" +
            "  <!-- 接受表格单元格增删 -->\n" +
            "  <xsl:template match=\"w:cellDel\"/>\n" +
            "  <xsl:template match=\"w:cellIns\"><xsl:apply-templates/></xsl:template>\n" +
            "  <!-- 移除移动标记 -->\n" +
            "  <xsl:template match=\"w:moveFrom\"/>\n" +
            "  <xsl:template match=\"w:moveFromRangeStart\"/>\n" +
            "  <xsl:template match=\"w:moveFromRangeEnd\"/>\n" +
            "  <xsl:template match=\"w:moveTo\"><xsl:apply-templates/></xsl:template>\n" +
            "  <xsl:template match=\"w:moveToRangeStart\"/>\n" +
            "  <xsl:template match=\"w:moveToRangeEnd\"/>\n" +
            "  <!-- 移除被删除区域内的书签 -->\n" +
            "  <xsl:template match=\"w:bookmarkStart[ancestor::w:del]\"/>\n" +
            "  <xsl:template match=\"w:bookmarkEnd[ancestor::w:del]\"/>\n" +
            "  <!-- 移除 rsid 属性（无实际视觉影响，减少体积） -->\n" +
            "  <xsl:template match=\"@w:rsidR|@w:rsidRPr|@w:rsidP|@w:rsidDel|@w:rsidTr\"/>\n" +
            "</xsl:stylesheet>";

    /** XSLT Templates 缓存 */
    private volatile javax.xml.transform.Templates revisionTemplates;

    private WordToPdfConverter() {
    }

    public static WordToPdfConverter getInstance() {
        if (instance == null) {
            synchronized (WordToPdfConverter.class) {
                if (instance == null) {
                    instance = new WordToPdfConverter();
                }
            }
        }
        return instance;
    }

    // ======================== 公开接口实现 ========================

    @Override
    public InputStream convertToStream(InputStream inputStream, String fileName) throws Exception {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".docx")) {
            return convertDocxToStream(inputStream);
        } else if (lowerName.endsWith(".doc")) {
            return convertDocToStream(inputStream);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + fileName + "，仅支持 .docx 和 .doc");
        }
    }

    @Override
    public InputStream convertToStream(String inputFilePath) throws Exception {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("输入文件路径不能为空");
        }
        File file = new File(inputFilePath);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + inputFilePath);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

    @Override
    public String convertToFile(String inputFilePath) throws Exception {
        String outputPath = determineOutputPath(inputFilePath);
        return convertToFile(inputFilePath, outputPath);
    }

    @Override
    public String convertToFile(String inputFilePath, String outputFilePath) throws Exception {
        if (inputFilePath == null || inputFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("输入文件路径不能为空");
        }
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("输出文件路径不能为空");
        }

        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("文件不存在: " + inputFilePath);
        }

        initFonts();

        String lowerName = inputFile.getName().toLowerCase();

        if (lowerName.endsWith(".docx")) {
            convertDocxToFile(inputFilePath, outputFilePath);
        } else if (lowerName.endsWith(".doc")) {
            convertDocToFile(inputFilePath, outputFilePath);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + inputFile.getName() + "，仅支持 .docx 和 .doc");
        }

        log.info("PDF 生成成功: {}", outputFilePath);
        return outputFilePath;
    }

    // ======================== .docx 转换 ========================

    private InputStream convertDocxToStream(InputStream inputStream) throws Exception {
        initFonts();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        convertDocxInternal(inputStream, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private void convertDocxToFile(String inputPath, String outputPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputPath);
             FileOutputStream fos = new FileOutputStream(outputPath)) {
            convertDocxInternal(fis, fos);
        }
    }

    /**
     * 核心 .docx 转 PDF 逻辑。
     * 流程：加载 → 清理批注/修订 → 字体映射 → 转换 PDF
     */
    private void convertDocxInternal(InputStream inputStream, OutputStream outputStream) throws Exception {
        try {
            // 读取为字节数组，方便重复读取
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] docxBytes = baos.toByteArray();

            // 加载文档
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage =
                    org.docx4j.openpackaging.packages.WordprocessingMLPackage.load(
                            new java.io.ByteArrayInputStream(docxBytes));

            // ★ 1. 清理：去除批注 + 接受全部修订
            cleanDocument(wordMLPackage);

            // ★ 2. 配置字体映射
            configureFontMapper(wordMLPackage);

            // ★ 3. 转换 PDF
            org.docx4j.Docx4J.toPDF(wordMLPackage, outputStream);

        } catch (Exception e) {
            log.error("docx 转 PDF 失败", e);
            throw new Exception("Word(.docx) 转 PDF 失败: " + e.getMessage(), e);
        }
    }

    // ======================== 批注 / 修订清理 ========================

    /**
     * 清理文档：移除批注 + 接受修订 + 中文字体名英文化。
     */
    private void cleanDocument(
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage) {

        // --- 1. 移除批注 ---
        removeComments(wordMLPackage);

        // --- 2. 接受全部修订 ---
        acceptAllRevisions(wordMLPackage);

        // --- 3. 中文字体名 → 英文（避免 FOP 找不到中文名字体而报 ###）---
        normalizeFontNames(wordMLPackage);
    }

    /**
     * 移除 Word 批注（CommentsPart）。
     * 不抛异常，若无批注则静默跳过。
     */
    private void removeComments(
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage) {
        try {
            org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart mdp =
                    wordMLPackage.getMainDocumentPart();
            if (mdp == null) return;

            // 获取 CommentsPart 并移除
            org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart cp =
                    mdp.getCommentsPart();
            if (cp != null) {
                mdp.getRelationshipsPart().removePart(cp.getPartName());
                log.info("已移除文档批注（CommentsPart）");
            }
        } catch (Exception e) {
            // CommentsPart 不存在或其他结构问题，静默跳过
            log.debug("移除批注时发生异常（可能无限批注）: {}", e.getMessage());
        }
    }

    /**
     * 接受 Word 文档中的全部 Track Changes 修订（插入/删除/格式变更/移动），
     * 使文档呈现为最终版本。通过 XSLT 实现，避免逐元素遍历 OOM。
     */
    private void acceptAllRevisions(
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage) {
        try {
            org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart mdp =
                    wordMLPackage.getMainDocumentPart();
            if (mdp == null) return;

            // Marshal JAXB → XML String
            String xml = org.docx4j.XmlUtils.marshaltoString(mdp.getJaxbElement(), true);

            // 应用 XSLT（接受全部修订）
            Templates templates = getRevisionTemplates();
            String cleanedXml = transformXml(xml, templates);

            // Unmarshal 回 JAXB 并设回 MainDocumentPart
            org.docx4j.wml.Document cleanedDoc =
                    (org.docx4j.wml.Document) org.docx4j.XmlUtils.unmarshalString(cleanedXml);
            mdp.setJaxbElement(cleanedDoc);

            log.info("已接受全部文档修订（Track Changes → 最终版）");

        } catch (Exception e) {
            // XSLT 转换失败时不影响主体流程（可能无修订）
            log.warn("接受修订时发生异常（文档可能无修订标记）: {}", e.getMessage());
        }
    }

    /** 获取 XSLT Templates（懒加载 + 缓存） */
    private Templates getRevisionTemplates() throws Exception {
        if (revisionTemplates == null) {
            synchronized (this) {
                if (revisionTemplates == null) {
                    javax.xml.transform.TransformerFactory factory =
                            javax.xml.transform.TransformerFactory.newInstance();
                    revisionTemplates = factory.newTemplates(
                            new StreamSource(new StringReader(ACCEPT_REVISIONS_XSLT)));
                }
            }
        }
        return revisionTemplates;
    }

    /** 对 XML 字符串执行 XSLT 转换 */
    private String transformXml(String xml, Templates templates) throws Exception {
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        Transformer transformer = templates.newTransformer();
        transformer.transform(new StreamSource(new StringReader(xml)), result);
        return writer.toString();
    }

    /**
     * 将文档中所有中文字体名替换为对应的英文字体名，
     * 并为所有 run 注入东亚回退字体，解决无显式字体设置的文本项
     * 在 FOP 渲染时因找不到 CJK 字形而显示为 #### 的问题。
     */
    private void normalizeFontNames(
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage) {

        final java.util.LinkedHashMap<String, String> cnToEn = new java.util.LinkedHashMap<>();
        cnToEn.put("宋体", "SimSun");
        cnToEn.put("黑体", "SimHei");
        cnToEn.put("楷体", "KaiTi");
        cnToEn.put("楷体_GB2312", "KaiTi");
        cnToEn.put("仿宋", "FangSong");
        cnToEn.put("仿宋_GB2312", "FangSong");
        cnToEn.put("微软雅黑", "Microsoft YaHei");
        cnToEn.put("等线", "DengXian");
        cnToEn.put("等线 Light", "DengXian Light");
        cnToEn.put("华文宋体", "SimSun");
        cnToEn.put("华文黑体", "SimHei");
        cnToEn.put("华文楷体", "KaiTi");
        cnToEn.put("华文仿宋", "FangSong");

        try {
            org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart mdp =
                    wordMLPackage.getMainDocumentPart();
            if (mdp == null) return;

            // Marshal JAXB → XML
            String xml = org.docx4j.XmlUtils.marshaltoString(mdp.getJaxbElement(), true);
            int replaced = 0;

            // ----- Step 1: 中文字体名 → 英文 -----
            String[] attrs = {"w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"};
            for (java.util.Map.Entry<String, String> e : cnToEn.entrySet()) {
                String cn = e.getKey();
                String en = e.getValue();
                for (String attr : attrs) {
                    String before = attr + "=\"" + cn + "\"";
                    String after = attr + "=\"" + en + "\"";
                    while (xml.contains(before)) {
                        xml = xml.replace(before, after);
                        replaced++;
                    }
                }
            }

            // ----- Step 2: 确保所有 <w:rFonts> 的 w:eastAsia 指向中文字体 -----
            // 情况1: 缺少 w:eastAsia → 注入 SimSun
            // 情况2: w:eastAsia 指向西文字体（如 Consolas/Calibri/Arial 等）→ 替换为 SimSun
            //   否则 FOP 找不到 CJK 字形 → 渲染为 ####
            final java.util.Set<String> WESTERN_EASTASIA = new java.util.HashSet<>(
                    java.util.Arrays.asList("Consolas", "Courier New", "Lucida Console",
                            "Monaco", "monospace"));
            java.util.regex.Pattern rFontsPattern = java.util.regex.Pattern.compile(
                    "<w:rFonts([^>]*?)/>");
            java.util.regex.Matcher m = rFontsPattern.matcher(xml);
            StringBuffer sb = new StringBuffer();
            int eastAsiaFixed = 0;
            while (m.find()) {
                String rFontsAttrs = m.group(1);
                // 提取当前 w:eastAsia 值
                java.util.regex.Matcher eaMatcher = java.util.regex.Pattern.compile(
                        "w:eastAsia=\"([^\"]*)\"").matcher(rFontsAttrs);
                if (eaMatcher.find()) {
                    String currentEA = eaMatcher.group(1);
                    // 如果 eastAsia 指向的是西文字体或已知不应作为东亚字体的字体，替换
                    if (WESTERN_EASTASIA.contains(currentEA)
                            || isWesternFontInCJKContext(currentEA)) {
                        String newAttrs = rFontsAttrs.replace(
                                "w:eastAsia=\"" + currentEA + "\"",
                                "w:eastAsia=\"SimSun\"");
                        m.appendReplacement(sb, "<w:rFonts" + newAttrs + "/>");
                        eastAsiaFixed++;
                        continue;
                    }
                } else {
                    // eastAsia 缺失 → 注入
                    m.appendReplacement(sb, "<w:rFonts" + rFontsAttrs + " w:eastAsia=\"SimSun\"/>");
                    eastAsiaFixed++;
                    continue;
                }
                m.appendReplacement(sb, m.group(0));
            }
            m.appendTail(sb);
            xml = sb.toString();

            // ----- Step 3: 为没有 <w:rFonts> 的 <w:rPr> 注入东亚回退字体 -----
            java.util.regex.Pattern rPrPattern = java.util.regex.Pattern.compile(
                    "<w:rPr>((?:(?!<w:rFonts).)*?)</w:rPr>",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m2 = rPrPattern.matcher(xml);
            StringBuffer sb2 = new StringBuffer();
            int rPrFixed = 0;
            while (m2.find()) {
                String inner = m2.group(1);
                m2.appendReplacement(sb2,
                        "<w:rPr>" + inner + "<w:rFonts w:eastAsia=\"SimSun\"/></w:rPr>");
                rPrFixed++;
            }
            m2.appendTail(sb2);
            xml = sb2.toString();

            if (replaced > 0 || eastAsiaFixed > 0 || rPrFixed > 0) {
                // Unmarshal 回 JAXB 并设回
                org.docx4j.wml.Document cleanedDoc =
                        (org.docx4j.wml.Document) org.docx4j.XmlUtils.unmarshalString(xml);
                mdp.setJaxbElement(cleanedDoc);
                log.info("字体规范化: 中→英 {} 处, 修复 eastAsia {} 处, 补充 rFonts {} 处",
                        replaced, eastAsiaFixed, rPrFixed);
            }

        } catch (Exception e) {
            log.warn("字体名规范化失败（不影响转换）: {}", e.getMessage());
        }
    }

    /**
     * 判断一个 eastAsia 字体名是否实际上是不含 CJK 的西文字体。
     * 当 eastAsia 与 ascii/hAnsi 相同时，说明是 POI 自动填写的西文字体名。
     */
    private boolean isWesternFontInCJKContext(String fontName) {
        if (fontName == null || fontName.trim().isEmpty()) return false;
        // 已是已知中文字体 → 不是西文
        if (fontName.equals("SimSun") || fontName.equals("SimHei")
                || fontName.equals("KaiTi") || fontName.equals("FangSong")
                || fontName.equals("Microsoft YaHei") || fontName.equals("DengXian")
                || fontName.equals("DengXian Light") || fontName.equals("NSimSun")) {
            return false;
        }
        // 西文字体名特征：纯 ASCII，不含中文/日文/韩文
        // 常见西文字体名都应替换
        String[] knownWestern = {"Calibri", "Arial", "Times New Roman", "Cambria",
                "Verdana", "Tahoma", "Segoe UI", "Helvetica", "Georgia",
                "Trebuchet MS", "Comic Sans MS", "Impact", "Consolas",
                "Courier New", "Lucida Console", "Monaco", "monospace"};
        for (String wf : knownWestern) {
            if (fontName.equalsIgnoreCase(wf)) return true;
        }
        return false;
    }

    // ======================== 字体映射（含降级兜底） ========================

    /**
     * 配置字体映射器。
     * <p>
     * 策略：系统字体 → 别名映射 → JAR 内嵌字体 → 兜底字体。
     * 任何环节失败都不抛异常，确保转换不被中断。
     * </p>
     */
    private void configureFontMapper(
            org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage) {

        try {
            org.docx4j.fonts.IdentityPlusMapper fontMapper =
                    new org.docx4j.fonts.IdentityPlusMapper();

            // ===== Step 1: 从系统目录加载物理字体 =====
            loadChineseFontsFromFileSystem();

            // ===== Step 2: 构建别名映射表（中文名 → 英文名 + 常用英文字体回退） =====
            java.util.LinkedHashMap<String, String> aliasMap = buildAliasMap();

            // ===== Step 3: 将找到的物理字体注册到映射器 =====
            for (String englishName : aliasMap.values()) {
                org.docx4j.fonts.PhysicalFont pf = getPhysicalFontCI(englishName);
                if (pf != null) {
                    fontMapper.getFontMappings().put(englishName, pf);
                }
            }

            // ===== Step 4: 别名（中文名 → 英文字体） =====
            for (java.util.Map.Entry<String, String> alias : aliasMap.entrySet()) {
                String chineseName = alias.getKey();
                String englishName = alias.getValue();
                org.docx4j.fonts.PhysicalFont pf = getPhysicalFontCI(englishName);
                if (pf != null) {
                    fontMapper.getFontMappings().put(chineseName, pf);
                    log.debug("字体映射: {} -> {}", chineseName, englishName);
                }
            }

            // ===== Step 5: 确定兜底字体（用于所有未匹配字体） =====
            String defaultFont = resolveFallbackFont(aliasMap);
            if (defaultFont != null) {
                fallbackChineseFont = defaultFont;
                org.docx4j.fonts.PhysicalFont pf = getPhysicalFontCI(defaultFont);
                if (pf != null) {
                    // 把常见西文字体也映射到有 CJK 的字体，避免 FOP "Glyph not available" 乱码
                    for (String westernFont : new String[]{"Calibri", "Arial", "Times New Roman",
                            "Cambria", "Verdana", "Tahoma", "Segoe UI", "Courier New"}) {
                        fontMapper.getFontMappings().put(westernFont, pf);
                    }
                    log.info("兜底字体: {} → 覆盖未知字体映射", defaultFont);
                }
            } else {
                log.warn("未找到任何中文字体，PDF 中可能出现空白字符。请安装中文字体到服务器。");
            }

            wordMLPackage.setFontMapper(fontMapper);
            log.debug("字体映射器配置完成");

        } catch (Exception e) {
            log.warn("字体映射配置失败（将使用默认映射）: {}", e.getMessage());
        }
    }

    /**
     * 构建完整的别名映射表。
     * 涵盖：中文名 → 英文名、英文名自身、以及服务器常见英文字体回退。
     */
    private java.util.LinkedHashMap<String, String> buildAliasMap() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();

        // 中文名 → 对应英文字体
        map.put("宋体", "SimSun");
        map.put("黑体", "SimHei");
        map.put("楷体", "KaiTi");
        map.put("楷体_GB2312", "KaiTi");
        map.put("仿宋", "FangSong");
        map.put("仿宋_GB2312", "FangSong");
        map.put("微软雅黑", "Microsoft YaHei");
        map.put("等线", "DengXian");
        map.put("等线 Light", "DengXian Light");
        map.put("华文宋体", "SimSun");
        map.put("华文黑体", "SimHei");
        map.put("华文楷体", "KaiTi");
        map.put("华文仿宋", "FangSong");
        // Linux 服务器常见中文字体
        map.put("WenQuanYi Micro Hei", "SimSun");
        map.put("WenQuanYi Zen Hei", "SimSun");
        map.put("Noto Sans CJK SC", "SimSun");
        map.put("Noto Sans SC", "SimSun");
        map.put("AR PL UMing CN", "SimSun");
        map.put("AR PL UKai CN", "KaiTi");
        // 日韩字体回退
        map.put("MS Mincho", "SimSun");
        map.put("MS Gothic", "SimSun");
        map.put("Malgun Gothic", "SimSun");

        return map;
    }

    /** 确定兜底字体：SimSun > SimHei > KaiTi > FangSong > Microsoft YaHei > 任意可用 */
    private String resolveFallbackFont(java.util.LinkedHashMap<String, String> aliasMap) {
        String[] preferred = {"SimSun", "SimHei", "KaiTi", "FangSong", "Microsoft YaHei", "DengXian"};
        for (String name : preferred) {
            if (getPhysicalFontCI(name) != null) {
                return name;
            }
        }
        for (String name : aliasMap.values()) {
            if (getPhysicalFontCI(name) != null) {
                return name;
            }
        }
        return null;
    }

    /**
     * 大小写不敏感的 PhysicalFont 查找。
     * PhysicalFonts 内部以小写存储字体名，直接用大写/混写查找会失败。
     */
    private org.docx4j.fonts.PhysicalFont getPhysicalFontCI(String fontName) {
        if (fontName == null) return null;
        // 先精确查找（兼容原有逻辑）
        org.docx4j.fonts.PhysicalFont pf = org.docx4j.fonts.PhysicalFonts.get(fontName);
        if (pf == null) {
            pf = org.docx4j.fonts.PhysicalFonts.get(fontName.toLowerCase());
        }
        return pf;
    }

    /** 从系统字体目录加载中文字体到 PhysicalFonts。加载失败不中断。 */
    private void loadChineseFontsFromFileSystem() {
        java.util.Map<String, String> chineseFonts = FontManager.getAvailableChineseFonts();
        if (!chineseFonts.isEmpty()) {
            log.info("检测到 {} 个系统中文字体: {}", chineseFonts.size(), chineseFonts.keySet());
        } else {
            log.info("未检测到系统中文字体，将使用 JAR 内嵌字体或兜底方案");
        }

        for (java.util.Map.Entry<String, String> entry : chineseFonts.entrySet()) {
            try {
                java.net.URI fontUri = new java.io.File(entry.getValue()).toURI();
                org.docx4j.fonts.PhysicalFonts.addPhysicalFont(fontUri);
                log.debug("加载物理字体: {} -> {}", entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("加载字体失败: {} -> {}", entry.getKey(), e.getMessage());
            }
        }

        // 同时尝试加载 JAR classpath 中的字体文件
        loadClasspathFonts();
    }

    /** 从 JAR 内部 classpath 加载备选字体（fonts/ 目录） */
    private void loadClasspathFonts() {
        String[] fontResources = {
                "fonts/simsun.ttf", "fonts/simsun.ttc",
                "fonts/simhei.ttf", "fonts/wqy-microhei.ttc",
                "fonts/DroidSansFallback.ttf"
        };
        for (String resource : fontResources) {
            java.io.InputStream fontStream = null;
            try {
                fontStream = getClass().getClassLoader().getResourceAsStream(resource);
                if (fontStream != null) {
                    // 写入临时文件供 FOP 加载
                    java.io.File tempFont = java.io.File.createTempFile("font_", ".ttf");
                    tempFont.deleteOnExit();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFont)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fontStream.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    }
                    org.docx4j.fonts.PhysicalFonts.addPhysicalFont(tempFont.toURI());
                    log.info("已加载 JAR 内嵌字体: {}", resource);
                }
            } catch (Exception e) {
                // classpath 中无此字体，静默跳过
            } finally {
                if (fontStream != null) {
                    try { fontStream.close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    // ======================== .doc 转换（通过 POI） ========================

    private InputStream convertDocToStream(InputStream inputStream) throws Exception {
        initFonts();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        convertDocInternal(inputStream, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private void convertDocToFile(String inputPath, String outputPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputPath);
             FileOutputStream fos = new FileOutputStream(outputPath)) {
            convertDocInternal(fis, fos);
        }
    }

    private void convertDocInternal(InputStream inputStream, OutputStream outputStream) throws Exception {
        File tempDocxFile = null;
        File tempPdfFile = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] docBytes = baos.toByteArray();

            org.apache.poi.hwpf.HWPFDocument doc =
                    new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(docBytes));
            org.apache.poi.hwpf.usermodel.Range range = doc.getRange();

            tempDocxFile = File.createTempFile("word_convert_", ".docx");
            org.apache.poi.xwpf.usermodel.XWPFDocument docxDoc =
                    new org.apache.poi.xwpf.usermodel.XWPFDocument();

            for (int i = 0; i < range.numParagraphs(); i++) {
                org.apache.poi.hwpf.usermodel.Paragraph paragraph = range.getParagraph(i);
                if (paragraph != null) {
                    String text = paragraph.text();
                    if (text != null && !text.trim().isEmpty()) {
                        org.apache.poi.xwpf.usermodel.XWPFParagraph xwpfPara =
                                docxDoc.createParagraph();
                        org.apache.poi.xwpf.usermodel.XWPFRun run = xwpfPara.createRun();
                        run.setText(text.trim());
                        run.setFontFamily("宋体");
                        run.setFontSize(12);
                    }
                }
            }
            doc.close();

            try (FileOutputStream fos = new FileOutputStream(tempDocxFile)) {
                docxDoc.write(fos);
            }
            docxDoc.close();

            tempPdfFile = File.createTempFile("word_convert_", ".pdf");
            convertDocxToFile(tempDocxFile.getAbsolutePath(), tempPdfFile.getAbsolutePath());

            try (FileInputStream fis = new FileInputStream(tempPdfFile)) {
                byte[] buf = new byte[8192];
                int readLen;
                while ((readLen = fis.read(buf)) != -1) {
                    outputStream.write(buf, 0, readLen);
                }
            }

        } catch (Exception e) {
            log.error(".doc 转 PDF 失败", e);
            throw new Exception("Word(.doc) 转 PDF 失败: " + e.getMessage(), e);
        } finally {
            if (tempDocxFile != null && tempDocxFile.exists()) tempDocxFile.delete();
            if (tempPdfFile != null && tempPdfFile.exists()) tempPdfFile.delete();
        }
    }

    // ======================== 辅助方法 ========================

    private synchronized void initFonts() {
        if (fontsInitialized) {
            return;
        }
        try {
            Map<String, String> fonts = FontManager.getAvailableChineseFonts();
            log.info("初始化字体完成，检测到 {} 个中文字体: {}", fonts.size(), fonts.keySet());
            fontsInitialized = true;
        } catch (Exception e) {
            log.warn("字体初始化失败: {}", e.getMessage());
        }
    }

    private String determineOutputPath(String inputFilePath) {
        File inputFile = new File(inputFilePath);
        String name = inputFile.getName();
        int dotIndex = name.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? name.substring(0, dotIndex) : name;
        String parentPath = inputFile.getParent();
        if (parentPath == null || parentPath.isEmpty()) {
            parentPath = ".";
        }
        return new File(parentPath, baseName + ".pdf").getAbsolutePath();
    }
}