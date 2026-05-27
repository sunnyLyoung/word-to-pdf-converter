package com.wordtopdf.converter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * WordToPdfConverter 测试类。
 * <p>
 * 测试前请准备测试文件：
 * 1. 在 test-data 目录下准备 test.docx 和 test.doc 文件
 * 2. 测试文件应包含中英文混排内容、表格、图片等
 * </p>
 */
public class WordToPdfConverterTest {

    private static final String TEST_DATA_DIR = "test-data";
    private static final String OUTPUT_DIR = "test-output";

    private WordToPdfConverter converter;

    @Before
    public void setUp() {
        converter = WordToPdfConverter.getInstance();
        // 创建输出目录
        new File(OUTPUT_DIR).mkdirs();
    }

    @After
    public void tearDown() {
        // 测试完成后保留输出文件供人工检查
    }

    /**
     * 测试 .docx 转 PDF（文件路径方式）
     */
    @Test
    public void testDocxToFile() throws Exception {
        String inputPath = TEST_DATA_DIR + File.separator + "test.docx";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.out.println("[跳过] 测试文件不存在: " + inputPath);
            System.out.println("请准备测试文件: " + inputFile.getAbsolutePath());
            return;
        }

        String outputPath = OUTPUT_DIR + File.separator + "test_output.docx.pdf";
        String result = converter.convertToFile(inputPath, outputPath);

        System.out.println("转换成功: " + result);

        File resultFile = new File(result);
        assert resultFile.exists() : "输出文件不存在";
        assert resultFile.length() > 0 : "输出文件为空";

        System.out.println("文件大小: " + resultFile.length() + " bytes");
    }

    /**
     * 测试 .docx 转 PDF（自动生成输出路径）
     */
    @Test
    public void testDocxToFileAutoPath() throws Exception {
        String inputPath = TEST_DATA_DIR + File.separator + "test.docx";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.out.println("[跳过] 测试文件不存在: " + inputPath);
            return;
        }

        String result = converter.convertToFile(inputPath);
        System.out.println("转换成功（自动路径）: " + result);

        File resultFile = new File(result);
        assert resultFile.exists() : "输出文件不存在";
        assert resultFile.length() > 0 : "输出文件为空";
    }

    /**
     * 测试 .docx 转 PDF（流方式）
     */
    @Test
    public void testDocxToStream() throws Exception {
        String inputPath = TEST_DATA_DIR + File.separator + "test.docx";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.out.println("[跳过] 测试文件不存在: " + inputPath);
            return;
        }

        try (InputStream inputStream = new FileInputStream(inputPath);
             InputStream pdfStream = converter.convertToStream(inputStream, inputFile.getName())) {

            String outputPath = OUTPUT_DIR + File.separator + "test_output_stream.pdf";
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = pdfStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }

            System.out.println("流转换成功: " + outputPath);

            File resultFile = new File(outputPath);
            assert resultFile.exists() : "输出文件不存在";
            assert resultFile.length() > 0 : "输出文件为空";

            System.out.println("文件大小: " + resultFile.length() + " bytes");
        }
    }

    /**
     * 测试 .docx 转 PDF（流方式 - 传入全路径）
     */
    @Test
    public void testDocxToStreamFromPath() throws Exception {
        String inputPath = TEST_DATA_DIR + File.separator + "test.docx";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.out.println("[跳过] 测试文件不存在: " + inputPath);
            return;
        }

        try (InputStream pdfStream = converter.convertToStream(inputPath)) {
            String outputPath = OUTPUT_DIR + File.separator + "test_output_stream2.pdf";
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = pdfStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }

            System.out.println("流转换成功（路径）: " + outputPath);

            File resultFile = new File(outputPath);
            assert resultFile.exists() : "输出文件不存在";
            assert resultFile.length() > 0 : "输出文件为空";
        }
    }

    /**
     * 测试 .doc 转 PDF（文件路径方式）
     */
    @Test
    public void testDocToFile() throws Exception {
        String inputPath = TEST_DATA_DIR + File.separator + "test.doc";
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.out.println("[跳过] 测试文件不存在: " + inputPath);
            System.out.println("请准备测试文件: " + inputFile.getAbsolutePath());
            return;
        }

        String outputPath = OUTPUT_DIR + File.separator + "test_output.doc.pdf";
        String result = converter.convertToFile(inputPath, outputPath);

        System.out.println("doc 转换成功: " + result);

        File resultFile = new File(result);
        assert resultFile.exists() : "输出文件不存在";
        assert resultFile.length() > 0 : "输出文件为空";

        System.out.println("文件大小: " + resultFile.length() + " bytes");
    }

    /**
     * 测试无效文件格式
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFormat() throws Exception {
        converter.convertToStream(new ByteArrayInputStream("test".getBytes()), "test.txt");
    }

    /**
     * 测试 null 参数
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullInputStream() throws Exception {
        converter.convertToStream(null, "test.docx");
    }

    /**
     * 测试文件不存在
     */
    @Test(expected = FileNotFoundException.class)
    public void testFileNotFound() throws Exception {
        converter.convertToFile(TEST_DATA_DIR + File.separator + "not_exist_file.docx");
    }

    /**
     * 生成测试 .docx 文件（仅用于测试目的）
     */
    @Test
    public void generateTestDocx() throws Exception {
        String outputPath = TEST_DATA_DIR + File.separator + "test.docx";
        new File(TEST_DATA_DIR).mkdirs();

        org.apache.poi.xwpf.usermodel.XWPFDocument document =
                new org.apache.poi.xwpf.usermodel.XWPFDocument();

        // 标题
        org.apache.poi.xwpf.usermodel.XWPFParagraph titlePara = document.createParagraph();
        titlePara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("Word 转 PDF 测试文档");
        titleRun.setBold(true);
        titleRun.setFontSize(20);
        titleRun.setFontFamily("微软雅黑");

        // 空行
        document.createParagraph();

        // 中文段落
        org.apache.poi.xwpf.usermodel.XWPFParagraph cnPara = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun cnRun = cnPara.createRun();
        cnRun.setText("这是一段中文测试文本。用于验证Word文档转换为PDF后，中文字体是否能够正常显示，"
                + "不会出现乱码现象。本项目基于 Apache POI 和 docx4j 实现，完全免费，无需第三方商业许可。");
        cnRun.setFontSize(14);
        cnRun.setFontFamily("宋体");

        // 英文段落
        org.apache.poi.xwpf.usermodel.XWPFParagraph enPara = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun enRun = enPara.createRun();
        enRun.setText("This is an English test paragraph. It verifies that English text "
                + "displays correctly after Word to PDF conversion. The converter is built "
                + "using Apache POI and docx4j, both under Apache 2.0 license.");
        enRun.setFontSize(12);
        enRun.setFontFamily("Times New Roman");

        // 中英混合
        document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFParagraph mixPara = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun mixRun = mixPara.createRun();
        mixRun.setText("中英混合 Mixed Content: 这是中文content mixed with English内容。");
        mixRun.setFontSize(12);

        // 表格
        document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable(3, 3);
        table.setWidth("100%");

        String[][] data = {
                {"姓名 Name", "年龄 Age", "城市 City"},
                {"张三", "28", "北京"},
                {"李四", "32", "上海"}
        };

        for (int row = 0; row < data.length; row++) {
            org.apache.poi.xwpf.usermodel.XWPFTableRow tableRow = table.getRow(row);
            for (int col = 0; col < data[row].length; col++) {
                org.apache.poi.xwpf.usermodel.XWPFTableCell cell =
                        tableRow.getCell(col);
                if (cell.getParagraphs().size() > 0) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph cellPara =
                            cell.getParagraphs().get(0);
                    org.apache.poi.xwpf.usermodel.XWPFRun cellRun = cellPara.createRun();
                    cellRun.setText(data[row][col]);
                    cellRun.setFontSize(11);
                }
            }
        }

        // 多种字体测试
        document.createParagraph();
        String[] fontNames = {"宋体", "黑体", "楷体", "仿宋", "微软雅黑"};
        String[] fontTexts = {"宋体测试文本 SimSun", "黑体测试文本 SimHei",
                "楷体测试文本 KaiTi", "仿宋测试文本 FangSong",
                "微软雅黑测试文本 Microsoft YaHei"};

        for (int i = 0; i < fontNames.length; i++) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph fp = document.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun fr = fp.createRun();
            fr.setText(fontTexts[i]);
            fr.setFontFamily(fontNames[i]);
            fr.setFontSize(14);
        }

        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            document.write(fos);
        }
        document.close();

        System.out.println("测试文件已生成: " + new File(outputPath).getAbsolutePath());
        System.out.println("文件大小: " + new File(outputPath).length() + " bytes");
    }

    /**
     * 完整流程演示（含修订清理验证）
     */
    @Test
    public void fullDemo() throws Exception {
        System.out.println("==================== Word 转 PDF 完整演示 ====================");

        // 1. 生成测试文件
        generateTestDocx();

        // 2. 获取转换器实例
        WordToPdfConverter conv = WordToPdfConverter.getInstance();

        // 3. 方式一：文件路径转换（返回 PDF 文件全路径）
        String testFile = TEST_DATA_DIR + File.separator + "test.docx";
        String pdfPath = conv.convertToFile(testFile,
                OUTPUT_DIR + File.separator + "demo_output.pdf");
        System.out.println("方式一（文件路径）: " + pdfPath);

        // 4. 方式二：流方式转换（返回 PDF 输入流）
        try (InputStream pdfStream = conv.convertToStream(testFile)) {
            String streamOutput = OUTPUT_DIR + File.separator + "demo_stream_output.pdf";
            try (FileOutputStream fos = new FileOutputStream(streamOutput)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = pdfStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }
            System.out.println("方式二（流方式）: " + streamOutput);
        }

        System.out.println("==================== 演示完成 ====================");
    }

    /**
     * 测试：含批注的文档转换（验证 CommentsPart 移除不报错）
     */
    @Test
    public void testDocxWithCommentsCleaning() throws Exception {
        System.out.println("==================== 批注清理测试 ====================");

        // 生成测试 docx
        generateTestDocx();

        // 直接转换 — cleanDocument() 会在内部调用 removeComments()
        String testFile = TEST_DATA_DIR + File.separator + "test.docx";
        String pdfPath = converter.convertToFile(testFile,
                OUTPUT_DIR + File.separator + "test_cleaned.pdf");

        File resultFile = new File(pdfPath);
        assert resultFile.exists() : "输出文件不存在";
        assert resultFile.length() > 0 : "输出文件为空";
        System.out.println("批注清理测试通过（无批注文档正常转换不报错）: " + pdfPath);
        System.out.println("文件大小: " + resultFile.length() + " bytes");
    }

    /**
     * 测试：纯英文 / 无中文字体环境下的降级兜底
     * （在无宋体等中文字体的环境下，应不报错、能生成 PDF）
     */
    @Test
    public void testFontFallbackGraceful() throws Exception {
        System.out.println("==================== 字体降级测试 ====================");

        // 生成最小 docx（纯英文，无中文，不依赖特定字体）
        String testFile = TEST_DATA_DIR + File.separator + "minimal.docx";
        new File(TEST_DATA_DIR).mkdirs();

        org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                new org.apache.poi.xwpf.usermodel.XWPFDocument();
        org.apache.poi.xwpf.usermodel.XWPFParagraph para = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = para.createRun();
        run.setText("Hello World. This is a minimal test document.");
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            doc.write(fos);
        }
        doc.close();

        String pdfPath = converter.convertToFile(testFile,
                OUTPUT_DIR + File.separator + "test_fallback.pdf");
        File resultFile = new File(pdfPath);
        assert resultFile.exists() : "输出文件不存在";
        assert resultFile.length() > 0 : "输出文件为空";
        System.out.println("字体降级测试通过: " + pdfPath);
        System.out.println("文件大小: " + resultFile.length() + " bytes");
    }

    /**
     * 快速验证：生成 docx 并转换 PDF（独立文件，避免 IDE 锁冲突）
     */
    @Test
    public void quickVerify() throws Exception {
        System.out.println("==================== 快速中文验证 ====================");

        String testFile = TEST_DATA_DIR + File.separator + "quick_test.docx";
        new File(TEST_DATA_DIR).mkdirs();

        org.apache.poi.xwpf.usermodel.XWPFDocument document =
                new org.apache.poi.xwpf.usermodel.XWPFDocument();

        org.apache.poi.xwpf.usermodel.XWPFParagraph p1 = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun r1 = p1.createRun();
        r1.setText("这是一段中文测试文本，用于验证中文字体是否正常显示。");
        r1.setFontSize(14);
        r1.setFontFamily("宋体");

        org.apache.poi.xwpf.usermodel.XWPFParagraph p2 = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun r2 = p2.createRun();
        r2.setText("黑体测试：中文字体转换应正常。");
        r2.setFontSize(14);
        r2.setFontFamily("黑体");

        org.apache.poi.xwpf.usermodel.XWPFParagraph p3 = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun r3 = p3.createRun();
        r3.setText("微软雅黑测试：Hello World 中文123。");
        r3.setFontSize(14);
        r3.setFontFamily("微软雅黑");

        org.apache.poi.xwpf.usermodel.XWPFParagraph p4 = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun r4 = p4.createRun();
        r4.setText("默认字体（Calibri回退）：中文应该由 SimSun 渲染，不应变成 ####。");
        r4.setFontSize(14);

        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            document.write(fos);
        }
        document.close();

        String pdfPath = converter.convertToFile(testFile,
                OUTPUT_DIR + File.separator + "quick_verify.pdf");
        System.out.println("PDF: " + pdfPath);

        File pdfFile = new File(pdfPath);
        assert pdfFile.exists() : "PDF 不存在";
        assert pdfFile.length() > 0 : "PDF 为空";
        System.out.println("大小: " + pdfFile.length() + " bytes —— 请检查 PDF 中中文是否正常");
        System.out.println("==================== 验证完成 ====================");
    }

    /**
     * 生成 WordToPdf 工具包使用说明 PDF，放到 jar 旁边。
     */
    @Test
    public void generateUsageGuide() throws Exception {
        System.out.println("==================== 生成使用说明 ====================");

        String guideDocx = TEST_DATA_DIR + File.separator + "usage_guide.docx";
        new File(TEST_DATA_DIR).mkdirs();

        org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                new org.apache.poi.xwpf.usermodel.XWPFDocument();

        // ---------- 标题 ----------
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun titleRun = title.createRun();
        titleRun.setText("WordToPdf 工具包使用说明");
        titleRun.setBold(true);
        titleRun.setFontSize(22);
        titleRun.setFontFamily("微软雅黑");

        // ---------- 版本信息 ----------
        doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFParagraph ver = doc.createParagraph();
        ver.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun verRun = ver.createRun();
        verRun.setText("版本 1.0.0  |  基于 Java 1.8  |  免费、开源（Apache 2.0）");
        verRun.setFontSize(11);
        verRun.setColor("666666");
        verRun.setFontFamily("宋体");

        // ---------- 分割线 ----------
        doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFParagraph sep = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun sepRun = sep.createRun();
        sepRun.setText("——————————————————————————————");
        sepRun.setFontSize(10);
        sepRun.setColor("999999");

        // ====== 1. 概述 ======
        addSection(doc, "一、项目概述");
        addBody(doc, "WordToPdf 是一个基于 Java 的 Word 转 PDF 工具包，封装为独立 JAR 文件（wordToPdf.jar），");
        addBody(doc, "可被其他 Java 项目直接引用。支持 .docx 和 .doc 格式的 Word 文档转换为 PDF，兼容各种中文字体，");
        addBody(doc, "不会出现乱码。");
        doc.createParagraph();
        addBody(doc, "核心特性：");
        addBullet(doc, "支持 .docx 和 .doc 格式转换为 PDF");
        addBullet(doc, "兼容中文字体（宋体、黑体、楷体、仿宋、微软雅黑等），不产乱码");
        addBullet(doc, "自动移除批注，只转换最终版本文档");
        addBullet(doc, "自动接受全部 Track Changes 修订");
        addBullet(doc, "支持企业 Logo / 遮盖层正常转换");
        addBullet(doc, "服务器缺失字体时自动降级兜底，不报错不产乱码");
        addBullet(doc, "统一接口：支持返回文件流（InputStream）或文件全路径");
        addBullet(doc, "依赖包经 Maven Shade 重定位，不会与调用方依赖冲突");
        addBullet(doc, "完全免费，无第三方商业许可证依赖");

        // ====== 2. 引入方式 ======
        doc.createParagraph();
        addSection(doc, "二、引入方式");
        addBody(doc, "将 wordToPdf.jar 拷贝到项目的 libs 目录，然后在 Maven 或 Gradle 中引入：");
        doc.createParagraph();

        // Maven
        org.apache.poi.xwpf.usermodel.XWPFParagraph mvnTitle = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun mvnTitleRun = mvnTitle.createRun();
        mvnTitleRun.setText("Maven：");
        mvnTitleRun.setBold(true);
        mvnTitleRun.setFontSize(12);

        addCode(doc, "<dependency>");
        addCode(doc, "    <groupId>com.wordtopdf</groupId>");
        addCode(doc, "    <artifactId>word-to-pdf-converter</artifactId>");
        addCode(doc, "    <version>1.0.0</version>");
        addCode(doc, "    <scope>system</scope>");
        addCode(doc, "    <systemPath>${project.basedir}/libs/wordToPdf.jar</systemPath>");
        addCode(doc, "</dependency>");

        doc.createParagraph();
        // Gradle
        org.apache.poi.xwpf.usermodel.XWPFParagraph gradleTitle = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun gradleTitleRun = gradleTitle.createRun();
        gradleTitleRun.setText("Gradle：");
        gradleTitleRun.setBold(true);
        gradleTitleRun.setFontSize(12);

        addCode(doc, "dependencies {");
        addCode(doc, "    implementation files('libs/wordToPdf.jar')");
        addCode(doc, "}");

        // ====== 3. 使用示例 ======
        doc.createParagraph();
        addSection(doc, "三、使用示例");

        addBody(doc, "3.1 获取转换器实例（单例模式）");
        addCode(doc, "WordToPdfConverter converter = WordToPdfConverter.getInstance();");

        doc.createParagraph();
        addBody(doc, "3.2 方式一：文件路径转换（返回 PDF 文件全路径）");
        addCode(doc, "String pdfPath = converter.convertToFile(\"/path/to/input.docx\");");
        addCode(doc, "// 或指定输出路径");
        addCode(doc, "String pdfPath = converter.convertToFile(");
        addCode(doc, "    \"/path/to/input.docx\",");
        addCode(doc, "    \"/path/to/output.pdf\");");

        doc.createParagraph();
        addBody(doc, "3.3 方式二：文件路径转换（返回 PDF 输入流）");
        addCode(doc, "InputStream pdfStream = converter.convertToStream(");
        addCode(doc, "    \"/path/to/input.docx\");");
        addCode(doc, "// 使用完记得关闭流");
        addCode(doc, "pdfStream.close();");

        doc.createParagraph();
        addBody(doc, "3.4 方式三：InputStream 输入（适用于 Web 上传等场景）");
        addCode(doc, "InputStream pdfStream = converter.convertToStream(");
        addCode(doc, "    inputStream,  // 上传的文件流");
        addCode(doc, "    \"report.docx\"  // 文件名（用于判断格式）");
        addCode(doc, ");");

        // ====== 4. 字体说明 ======
        doc.createParagraph();
        addSection(doc, "四、字体说明");
        addBody(doc, "本工具会自动检测服务器系统中安装的字体，并智能映射。");
        doc.createParagraph();
        addBody(doc, "字体查找优先级：");
        addBullet(doc, "1. 系统字体目录（Windows: C:\\Windows\\Fonts, Linux: /usr/share/fonts）");
        addBullet(doc, "2. JAR 内嵌字体（classpath 中的 fonts/ 目录）");
        addBullet(doc, "3. 兜底字体映射（SimSun → SimHei → KaiTi → FangSong → Microsoft YaHei）");
        doc.createParagraph();
        addBody(doc, "服务器部署建议：");
        addBullet(doc, "Windows 服务器：通常已内置宋体、黑体等，无需额外配置");
        addBullet(doc, "Linux 服务器：建议安装中文字体包");
        addBullet(doc, "  CentOS/RHEL: yum install -y fonts-chinese");
        addBullet(doc, "  Ubuntu/Debian: apt-get install -y fonts-wqy-zenhei fonts-wqy-microhei");
        addBullet(doc, "  Docker 镜像: 在 Dockerfile 中添加字体安装步骤");
        doc.createParagraph();
        addBody(doc, "如果服务器上完全没有任何中文字体，转换不会报错，但中文可能显示为空白。");

        // ====== 5. 注意事项 ======
        doc.createParagraph();
        addSection(doc, "五、注意事项");
        addBullet(doc, "1. 本工具适用于 Java 1.8 及以上版本");
        addBullet(doc, "2. .doc 格式转换会先转为 .docx 再转 PDF，转换质量取决于原文档复杂度");
        addBullet(doc, "3. Word 文档中的批注（Comments）和修订标记（Track Changes）会被自动清除");
        addBullet(doc, "4. 转换后的 PDF 不保留原文档的宏、ActiveX 控件等高级功能");
        addBullet(doc, "5. 依赖包已通过 Maven Shade Plugin 重定位到 com.wordtopdf.shaded.* 下，");
        addBullet(doc, "   不会与调用方项目中的 docx4j、POI、FOP 等依赖产生版本冲突");
        addBullet(doc, "6. 本工具基于 Apache 2.0 / EPL 2.0 开源协议，可免费商用");

        // ====== 6. 常见问题 ======
        doc.createParagraph();
        addSection(doc, "六、常见问题");
        addBody(doc, "Q: 转换后中文变成 #### 怎么办？");
        addBody(doc, "A: 升级到最新版 wordToPdf.jar。新版本已修复此问题，会对无显式字体设置的文本项");
        addBody(doc, "   自动注入中文字体回退。");
        doc.createParagraph();
        addBody(doc, "Q: 转换后报 ClassNotFoundException？");
        addBody(doc, "A: 请确保使用 Java 1.8 及以上版本，且只引入了 wordToPdf.jar，");
        addBody(doc, "   不要单独引入 docx4j、POI 等依赖。");
        doc.createParagraph();
        addBody(doc, "Q: Linux 服务器转换后中文不显示？");
        addBody(doc, "A: 请参考「四、字体说明」部分安装中文字体包。");
        doc.createParagraph();
        addBody(doc, "Q: 如何查看转换日志？");
        addBody(doc, "A: 本工具使用 SLF4J 日志门面。在项目的日志框架（如 Logback、Log4j2）中");
        addBody(doc, "   配置 com.wordtopdf 包的日志级别为 INFO 即可。");

        // 写入 docx
        try (FileOutputStream fos = new FileOutputStream(guideDocx)) {
            doc.write(fos);
        }
        doc.close();
        System.out.println("使用说明 docx 已生成: " + guideDocx);

        // 转换为 PDF
        String pdfPath = converter.convertToFile(guideDocx,
                OUTPUT_DIR + File.separator + "WordToPdf_使用说明.pdf");
        System.out.println("使用说明 PDF 已生成: " + pdfPath);

        File pdfFile = new File(pdfPath);
        assert pdfFile.exists() : "使用说明 PDF 不存在";
        assert pdfFile.length() > 0 : "使用说明 PDF 为空";
        System.out.println("文件大小: " + pdfFile.length() + " bytes");
        System.out.println("==================== 使用说明生成完成 ====================");
    }

    // ========== 辅助方法 ==========

    private void addSection(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(16);
        r.setFontFamily("黑体");
    }

    private void addBody(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        p.setSpacingBefore(0);
        org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(11);
        r.setFontFamily("宋体");
    }

    private void addBullet(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        p.setSpacingBefore(0);
        p.setIndentationLeft(420);
        org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
        r.setText("  " + text);
        r.setFontSize(11);
        r.setFontFamily("宋体");
    }

    private void addCode(org.apache.poi.xwpf.usermodel.XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        p.setSpacingBefore(0);
        p.setIndentationLeft(420);
        org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontSize(10);
        // 西文用 Consolas 等宽字体，东亚文字用 SimSun
        r.setFontFamily("Consolas");
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fonts =
                r.getCTR().isSetRPr() && r.getCTR().getRPr().isSetRFonts()
                        ? r.getCTR().getRPr().getRFonts()
                        : r.getCTR().addNewRPr().addNewRFonts();
        fonts.setEastAsia("SimSun");
    }
}