package com.wordtopdf.converter;

import java.io.InputStream;

/**
 * 文件转换统一接口。
 * 提供两种返回方式：文件流（InputStream）或转换后的文件全路径（String）。
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
}