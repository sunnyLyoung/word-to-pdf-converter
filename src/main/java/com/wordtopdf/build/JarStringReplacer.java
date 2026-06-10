package com.wordtopdf.build;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * 后处理 shaded JAR，替换 .properties / .xml 文件中未 relocation 的类名字符串。
 * maven-shade-plugin 只替换 .class 字节码，不更新 .properties/.xml 中的字符串。
 */
public class JarStringReplacer {

    private static final Map<String, String> REPLACEMENTS = new LinkedHashMap<>();

    static {
        // 类名格式替换（包名用 . 分隔）
        REPLACEMENTS.put("org.docx4j.",           "com.wordtopdf.shaded.docx4j.");
        REPLACEMENTS.put("org.apache.fop.",       "com.wordtopdf.shaded.fop.");
        REPLACEMENTS.put("org.apache.xmlgraphics.","com.wordtopdf.shaded.xmlgraphics.");
        REPLACEMENTS.put("org.apache.pdfbox.",    "com.wordtopdf.shaded.pdfbox.");
        REPLACEMENTS.put("org.apache.avalon.",    "com.wordtopdf.shaded.avalon.");
        REPLACEMENTS.put("org.apache.commons.",   "com.wordtopdf.shaded.commons.");
        REPLACEMENTS.put("org.apache.poi.",       "com.wordtopdf.shaded.poi.");
        REPLACEMENTS.put("org.apache.xmlbeans.",  "com.wordtopdf.shaded.xmlbeans.");
        REPLACEMENTS.put("javax.xml.bind.",       "com.wordtopdf.shaded.xml.bind.");
        REPLACEMENTS.put("javax.activation.",     "com.wordtopdf.shaded.activation.");
        REPLACEMENTS.put("org.glassfish.",        "com.wordtopdf.shaded.glassfish.");
        REPLACEMENTS.put("com.sun.xml.",          "com.wordtopdf.shaded.sun.xml.");
        REPLACEMENTS.put("com.sun.istack.",       "com.wordtopdf.shaded.sun.istack.");
        REPLACEMENTS.put("org.eclipse.persistence.","com.wordtopdf.shaded.eclipse.persistence.");
        REPLACEMENTS.put("javax.persistence.",    "com.wordtopdf.shaded.persistence.");

        // 资源路径格式替换（包名用 / 分隔，用于 .properties/.xml 中引用的资源路径）
        REPLACEMENTS.put("org/docx4j/",           "com/wordtopdf/shaded/docx4j/");
        REPLACEMENTS.put("org/apache/fop/",       "com/wordtopdf/shaded/fop/");
        REPLACEMENTS.put("org/apache/xmlgraphics/","com/wordtopdf/shaded/xmlgraphics/");
        REPLACEMENTS.put("org/apache/pdfbox/",    "com/wordtopdf/shaded/pdfbox/");
        REPLACEMENTS.put("org/apache/avalon/",    "com/wordtopdf/shaded/avalon/");
        REPLACEMENTS.put("org/apache/poi/",       "com/wordtopdf/shaded/poi/");
        REPLACEMENTS.put("org/apache/xmlbeans/",  "com/wordtopdf/shaded/xmlbeans/");
        REPLACEMENTS.put("javax/xml/bind/",       "com/wordtopdf/shaded/xml/bind/");
        REPLACEMENTS.put("javax/activation/",     "com/wordtopdf/shaded/activation/");
        REPLACEMENTS.put("org/glassfish/",        "com/wordtopdf/shaded/glassfish/");
        REPLACEMENTS.put("com/sun/xml/",          "com/wordtopdf/shaded/sun/xml/");
        REPLACEMENTS.put("com/sun/istack/",       "com/wordtopdf/shaded/sun/istack/");
        REPLACEMENTS.put("org/eclipse/persistence/","com/wordtopdf/shaded/eclipse/persistence/");
    }

    public static void main(String[] args) throws IOException {
        String jarPath = args[0];
        File inputJar = new File(jarPath);
        File outputJar = new File(jarPath + ".tmp");

        System.out.println("Processing: " + inputJar.getAbsolutePath());

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(inputJar));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputJar))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (entry.isDirectory()) {
                    // 目录条目直接拷贝
                    zos.putNextEntry(new ZipEntry(name));
                    zis.closeEntry();
                    zos.closeEntry();
                    continue;
                }

                // 需要处理文本替换的文件类型：.properties / .xml / META-INF/services/* 等
                // maven-shade-plugin 只替换 .class 字节码，不更新这些文本文件中的字符串引用
                boolean isTextFile = name.endsWith(".properties")
                    || name.endsWith(".xml")
                    || name.endsWith(".txt")
                    || name.endsWith(".cfg")
                    || name.startsWith("META-INF/services/");

                ZipEntry newEntry = new ZipEntry(name);
                newEntry.setMethod(entry.getMethod());
                newEntry.setTime(entry.getTime());
                if (entry.getComment() != null) {
                    newEntry.setComment(entry.getComment());
                }
                zos.putNextEntry(newEntry);

                if (isTextFile) {
                    // 读取全部内容，执行替换
                    byte[] content = readAllBytes(zis);
                    String text = new String(content, "UTF-8");
                    String replaced = text;
                    for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) {
                        if (replaced.contains(e.getKey())) {
                            replaced = replaced.replace(e.getKey(), e.getValue());
                        }
                    }
                    zos.write(replaced.getBytes("UTF-8"));
                } else {
                    // 非文本文件直接拷贝
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                }
                zis.closeEntry();
                zos.closeEntry();
            }
        }

        // 替换原文件
        inputJar.delete();
        outputJar.renameTo(inputJar);
        System.out.println("Done: class names replaced in " + inputJar.getAbsolutePath());
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }
}