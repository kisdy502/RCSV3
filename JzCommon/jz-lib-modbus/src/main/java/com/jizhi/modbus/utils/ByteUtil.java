package com.jizhi.modbus.utils;

/**
 * Created with IntelliJ IDEA.
 * User: linghufeixia
 * Date: 2022-12-7
 * Description: 接受res请求的策略
 */
public class ByteUtil {
    public static String bytesToHexString(byte[] bArray) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2) {
                sb.append(0);
            }
            sb.append(sTemp.toUpperCase()).append(" ");
        }
        return sb.toString();
    }

    public static byte[] hexStringToBytes(String src) {
        int l = src.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            ret[i] = (byte) Integer.valueOf(src.substring(i * 2, i * 2 + 2), 16).byteValue();
        }
        return ret;
    }


    /**
     * 整数转化为字节 大端模式
     *
     * @param n
     * @return
     */
    public static byte[] intToBytesBig(int n) {
        byte[] src = new byte[2];
        src[0] = (byte) ((n >> 8) & 0xFF);
        src[1] = (byte) (n & 0xFF);
        return src;
    }


    /**
     * byte数组中取int数值，本方法适用于(低位在前，高位在后)的顺序，和intToBytes配套使用
     *
     * @param src    byte数组
     * @param offset 从数组的第offset位开始
     * @return int数值
     */
    public static int bytesToInt(byte[] src, int offset) {
        return ((src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16) | ((src[offset + 3] & 0xFF) << 24));
    }

    /**
     * byte数组中取int数值，本方法适用于(低位在前，高位在后)的顺序，和intToBytes配套使用
     *
     * @param src byte数组
     * @return int数值
     */
    public static int bytesToInt(byte[] src) {
        return ByteUtil.bytesToInt(src, 0);
    }

    /**
     * 将字节数组转换成float数据
     *
     * @param bytes 字节数组
     * @return float值
     */
    public static float bytesToFloat(byte[] bytes) {
        return Float.intBitsToFloat(bytesToInt(bytes, 0));
    }

    /**
     * 将字节数组转换成float数据
     *
     * @param bytes  字节数组
     * @param offset 起始位置
     * @return float值
     */
    public static float bytesToFloat(byte[] bytes, int offset) {
        return Float.intBitsToFloat(bytesToInt(bytes, offset));
    }


    /**
     * 字节数组的复制
     *
     * @param sourceBytes
     * @param targetBytes
     * @param beginIndex
     * @return
     */
    public static byte[] copyBytes(byte[] sourceBytes, byte[] targetBytes, int beginIndex) {
        if (targetBytes == null) {
            return sourceBytes;
        }
        int targetSize = targetBytes.length;
        if (sourceBytes == null) {
            beginIndex = 0;
            sourceBytes = new byte[targetSize];
        }
        int sourceSize = sourceBytes.length;
        if (sourceSize - beginIndex < targetSize) {
            return sourceBytes;
        } else {
            for (int i = 0; i < targetSize; i++) {
                sourceBytes[beginIndex + i] = targetBytes[i];
            }
        }
        return sourceBytes;
    }

    public static byte[] convertToBytes(short[] sdata) {
        int byteCount = sdata.length * 2;
        byte[] data = new byte[byteCount];
        for (int i = 0; i < sdata.length; i++) {
            data[i * 2] = (byte) (0xff & (sdata[i] >> 8));
            data[i * 2 + 1] = (byte) (0xff & sdata[i]);
        }
        return data;
    }

}
