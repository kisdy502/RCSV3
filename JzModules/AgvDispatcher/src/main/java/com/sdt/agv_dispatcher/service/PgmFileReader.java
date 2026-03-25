package com.sdt.agv_dispatcher.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PGM文件解析工具类
 * 用于读取PGM地图文件的头部信息，获取图像尺寸等元数据
 */
@Component
@Slf4j
public class PgmFileReader {

    private static final String BINARY_PGM_MAGIC = "P5";
    private static final String ASCII_PGM_MAGIC = "P2";
    private static final char COMMENT_CHAR = '#';

    /**
     * 读取PGM文件头部信息，提取宽度和高度
     *
     * @param pgmFilePath PGM文件路径
     * @return 包含宽度和高度的数组 [width, height]
     * @throws IOException 文件读取异常
     */
    public int[] readPgmDimensions(String pgmFilePath) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(pgmFilePath))) {
            String magicNumber = readNextToken(stream);

            if (!magicNumber.equals(BINARY_PGM_MAGIC) && !magicNumber.equals(ASCII_PGM_MAGIC)) {
                throw new IOException("不支持的PGM格式，期望P2或P5，实际: " + magicNumber);
            }

            int width = Integer.parseInt(readNextToken(stream));
            int height = Integer.parseInt(readNextToken(stream));
            int maxGrayValue = Integer.parseInt(readNextToken(stream));

            log.debug("成功解析PGM文件: {}x{}, 最大灰度值: {}", width, height, maxGrayValue);

            return new int[]{width, height};
        }
    }

    /**
     * 读取下一个令牌，跳过注释和空白字符
     */
    private String readNextToken(InputStream stream) throws IOException {
        List<Byte> bytes = new ArrayList<>();

        while (true) {
            int b = stream.read();
            if (b == -1) {
                break;
            }

            char c = (char) b;
            if (c == COMMENT_CHAR) {
                // 跳过注释行直到换行
                int d;
                do {
                    d = stream.read();
                } while (d != -1 && d != '\n' && d != '\r');
            } else if (!Character.isWhitespace(c)) {
                bytes.add((byte) b);
            } else if (bytes.size() > 0) {
                break;
            }
        }

        byte[] bytesArray = new byte[bytes.size()];
        for (int i = 0; i < bytesArray.length; i++) {
            bytesArray[i] = bytes.get(i);
        }

        return new String(bytesArray);
    }

    /**
     * 完整的PGM文件信息读取
     */
    public PgmFileInfo readPgmFileInfo(String pgmFilePath) throws IOException {
        try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(pgmFilePath))) {
            String magicNumber = readNextToken(stream);
            int width = Integer.parseInt(readNextToken(stream));
            int height = Integer.parseInt(readNextToken(stream));
            int maxGrayValue = Integer.parseInt(readNextToken(stream));

            return new PgmFileInfo(magicNumber, width, height, maxGrayValue);
        }
    }

    /**
     * PGM文件信息封装类
     */
    @Getter
    public static class PgmFileInfo {
        // getter方法
        private final String magicNumber;
        private final int width;
        private final int height;
        private final int maxGrayValue;

        public PgmFileInfo(String magicNumber, int width, int height, int maxGrayValue) {
            this.magicNumber = magicNumber;
            this.width = width;
            this.height = height;
            this.maxGrayValue = maxGrayValue;
        }

    }
}
