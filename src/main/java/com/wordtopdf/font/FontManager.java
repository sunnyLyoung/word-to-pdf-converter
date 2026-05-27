package com.wordtopdf.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * 中文字体管理器。
 * 自动检测系统中可用的中文字体并注册到 docx4j 的字体映射中，避免转换后出现乱码。
 */
public class FontManager {

    private static final Logger log = LoggerFactory.getLogger(FontManager.class);

    /**
     * Windows 系统常见中文字体
     */
    private static final String[] WINDOWS_FONT_DIRS = {
            "C:\\Windows\\Fonts",
            "C:\\WINNT\\Fonts"
    };

    /**
     * Linux 系统常见中文字体目录
     */
    private static final String[] LINUX_FONT_DIRS = {
            "/usr/share/fonts",
            "/usr/local/share/fonts",
            "/usr/share/fonts/truetype",
            "/usr/share/fonts/opentype"
    };

    /**
     * macOS 系统常见中文字体目录
     */
    private static final String[] MAC_FONT_DIRS = {
            "/System/Library/Fonts",
            "/Library/Fonts",
            "~/Library/Fonts"
    };

    /**
     * 常见中文字体名称及其对应的字体文件名（Windows 系统）
     */
    private static final Map<String, String[]> CHINESE_FONT_MAPPING = new LinkedHashMap<>();

    static {
        CHINESE_FONT_MAPPING.put("宋体", new String[]{"simsun.ttf", "simsun.ttc", "SimSun.ttf", "SIMSUN.TTF"});
        CHINESE_FONT_MAPPING.put("SimSun", new String[]{"simsun.ttf", "simsun.ttc", "SimSun.ttf", "SIMSUN.TTF"});
        CHINESE_FONT_MAPPING.put("黑体", new String[]{"simhei.ttf", "SimHei.ttf", "SIMHEI.TTF"});
        CHINESE_FONT_MAPPING.put("SimHei", new String[]{"simhei.ttf", "SimHei.ttf", "SIMHEI.TTF"});
        CHINESE_FONT_MAPPING.put("微软雅黑", new String[]{"msyh.ttf", "msyh.ttc", "Microsoft YaHei.ttf"});
        CHINESE_FONT_MAPPING.put("Microsoft YaHei", new String[]{"msyh.ttf", "msyh.ttc", "Microsoft YaHei.ttf"});
        CHINESE_FONT_MAPPING.put("楷体", new String[]{"simkai.ttf", "SimKai.ttf", "SIMKAI.TTF"});
        CHINESE_FONT_MAPPING.put("KaiTi", new String[]{"simkai.ttf", "SimKai.ttf", "SIMKAI.TTF"});
        CHINESE_FONT_MAPPING.put("仿宋", new String[]{"simfang.ttf", "SimFang.ttf", "SIMFANG.TTF"});
        CHINESE_FONT_MAPPING.put("FangSong", new String[]{"simfang.ttf", "SimFang.ttf", "SIMFANG.TTF"});
        CHINESE_FONT_MAPPING.put("等线", new String[]{"Deng.ttf", "DengXian.ttf"});
        CHINESE_FONT_MAPPING.put("DengXian", new String[]{"Deng.ttf", "DengXian.ttf"});
    }

    /**
     * 获取系统中所有可用字体的目录列表
     */
    public static List<String> getSystemFontDirs() {
        List<String> dirs = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("windows")) {
            for (String dir : WINDOWS_FONT_DIRS) {
                File f = new File(dir);
                if (f.exists() && f.isDirectory()) {
                    dirs.add(dir);
                }
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            for (String dir : MAC_FONT_DIRS) {
                String resolved = dir.startsWith("~") ?
                        dir.replace("~", System.getProperty("user.home")) : dir;
                File f = new File(resolved);
                if (f.exists() && f.isDirectory()) {
                    dirs.add(resolved);
                }
            }
        } else {
            // Linux / Unix
            for (String dir : LINUX_FONT_DIRS) {
                File f = new File(dir);
                if (f.exists() && f.isDirectory()) {
                    dirs.add(dir);
                }
            }
        }

        log.debug("系统字体目录: {}", dirs);
        return dirs;
    }

    /**
     * 根据字体名称查找字体文件路径
     *
     * @param fontName 字体名称（如 "宋体"、"SimSun"、"微软雅黑"）
     * @return 字体文件路径，未找到返回 null
     */
    public static String findFontFile(String fontName) {
        if (fontName == null || fontName.trim().isEmpty()) {
            return null;
        }

        String normalized = fontName.trim();

        // 1. 先尝试通过 Java AWT GraphicsEnvironment 获取
        String path = findFontViaGraphicsEnvironment(normalized);
        if (path != null) {
            return path;
        }

        // 2. 通过系统字体目录查找
        String[] possibleNames = CHINESE_FONT_MAPPING.get(normalized);
        if (possibleNames == null) {
            // 尝试直接文件查找
            possibleNames = new String[]{normalized + ".ttf", normalized + ".ttc", normalized + ".otf"};
        }

        List<String> dirs = getSystemFontDirs();
        for (String dir : dirs) {
            for (String name : possibleNames) {
                File fontFile = new File(dir, name);
                if (fontFile.exists()) {
                    log.debug("找到字体文件: {} -> {}", normalized, fontFile.getAbsolutePath());
                    return fontFile.getAbsolutePath();
                }
            }
        }

        // 3. 递归搜索子目录
        for (String dir : dirs) {
            path = searchFontInDirectory(new File(dir), possibleNames);
            if (path != null) {
                return path;
            }
        }

        log.warn("未找到字体: {}", normalized);
        return null;
    }

    /**
     * 通过 Java AWT 获取系统字体路径
     */
    private static String findFontViaGraphicsEnvironment(String fontName) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font[] fonts = ge.getAllFonts();
            for (Font font : fonts) {
                if (font.getFamily().equalsIgnoreCase(fontName) ||
                        font.getFontName().equalsIgnoreCase(fontName) ||
                        font.getName().equalsIgnoreCase(fontName)) {
                    // AWT 不会直接提供文件路径，但我们可以通过 font 的其他属性获取
                    log.debug("AWT 找到字体: {}", font.getFontName());
                    // 返回 null 让调用方继续搜索
                }
            }
        } catch (Exception e) {
            log.debug("AWT 字体检测失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 递归搜索目录中的字体文件
     */
    private static String searchFontInDirectory(File dir, String[] names) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String result = searchFontInDirectory(file, names);
                if (result != null) {
                    return result;
                }
            } else if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                for (String name : names) {
                    if (fileName.equalsIgnoreCase(name)) {
                        log.debug("递归搜索找到字体: {}", file.getAbsolutePath());
                        return file.getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取系统中可用的中文字体文件路径映射表
     *
     * @return Map<字体名称, 字体文件路径>
     */
    public static Map<String, String> getAvailableChineseFonts() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String fontName : CHINESE_FONT_MAPPING.keySet()) {
            String path = findFontFile(fontName);
            if (path != null) {
                result.put(fontName, path);
            }
        }
        return result;
    }

    /**
     * 读取字体文件为 InputStream
     */
    public static InputStream getFontStream(String fontFilePath) throws Exception {
        if (fontFilePath == null) {
            return null;
        }
        File file = new File(fontFilePath);
        if (!file.exists()) {
            return null;
        }
        return new FileInputStream(file);
    }
}