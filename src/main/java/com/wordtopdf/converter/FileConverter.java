package com.wordtopdf.converter;

import java.io.InputStream;

/**
 * 文件转换统一接口。
 * 提供两种返回方式：文件流（InputStream）或转换后的文件全路径（String）。
 * <p>
 * 同时提供文档页数统计和文件复制功能。
 * </p>
 */
public interface FileConverter {

    /**
     * 将 Word 文件流转换为 PDF，返回 PDF 文件流。
     * 调用方负责关闭返回的 InputStream。
     *
     * @param inputStream Word 文件输入流
     * @param fileName    原始文件名（用于判断 .docx / .doc 格式）
     * @return PDF 文件流
     * @throws Exception 转换异常
     */
    InputStream convertToStream(InputStream inputStream, String fileName) throws Exception;

    /**
     * 将 Word 文件路径转换为 PDF，返回 PDF 文件流。
     * 调用方负责关闭返回的 InputStream。
     *
     * @param inputFilePath Word 文件路径
     * @return PDF 文件流
     * @throws Exception 转换异常
     */
    InputStream convertToStream(String inputFilePath) throws Exception;

    /**
     * 将 Word 文件转换为 PDF，返回生成的 PDF 文件全路径。
     * PDF 默认生成在与 Word 文件同目录，文件名同 Word 文件（扩展名改为 .pdf）。
     *
     * @param inputFilePath Word 文件路径
     * @return PDF 文件全路径
     * @throws Exception 转换异常
     */
    String convertToFile(String inputFilePath) throws Exception;

    /**
     * 将 Word 文件转换为 PDF，返回指定的 PDF 文件全路径。
     *
     * @param inputFilePath  Word 文件路径
     * @param outputFilePath 目标 PDF 文件路径
     * @return PDF 文件全路径
     * @throws Exception 转换异常
     */
    String convertToFile(String inputFilePath, String outputFilePath) throws Exception;

    // ========== 文档页数统计 ==========

    /**
     * 获取文档页数（文件路径方式）。
     * <p>
     * 支持格式：
     * <ul>
     *   <li>.docx / .doc — 先渲染为 PDF，再统计 PDF 页数</li>
     *   <li>.pdf — 直接读取 PDF 页数</li>
     * </ul>
     * 文件名后缀用于判断格式，其他格式将抛出异常。
     * </p>
     *
     * @param filePath 文档文件路径
     * @return 文档页数
     * @throws Exception 文件不存在、格式不支持或处理异常
     */
    int getPageCount(String filePath) throws Exception;

    /**
     * 获取文档页数（文件流方式）。
     * <p>
     * 支持格式：
     * <ul>
     *   <li>.docx / .doc — 先渲染为 PDF，再统计 PDF 页数</li>
     *   <li>.pdf — 直接读取 PDF 页数</li>
     * </ul>
     * 文件名后缀用于判断格式，其他格式将抛出异常。
     * </p>
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于判断格式）
     * @return 文档页数
     * @throws Exception 文件流为空、格式不支持或处理异常
     */
    int getPageCount(InputStream inputStream, String fileName) throws Exception;

    // ========== 文件复制 ==========

    /**
     * 文件复制（文件路径到文件路径）。
     * 如果目标目录不存在，则自动创建。
     *
     * @param sourceFilePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @return 目标文件全路径
     * @throws Exception 源文件不存在或复制失败
     */
    String copyFile(String sourceFilePath, String targetFilePath) throws Exception;

    /**
     * 文件复制（文件流到文件路径）。
     * 如果目标目录不存在，则自动创建。
     * 调用完成后会关闭输入流。
     *
     * @param inputStream    源文件输入流
     * @param targetFilePath 目标文件路径
     * @return 目标文件全路径
     * @throws Exception 流为空或复制失败
     */
    String copyFile(InputStream inputStream, String targetFilePath) throws Exception;
}