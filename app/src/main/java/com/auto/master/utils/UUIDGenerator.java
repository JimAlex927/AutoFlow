package com.auto.master.utils;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID 生成工具类
 * 提供多种 UUID 生成策略和格式转换功能
 */
public final class UUIDGenerator {

    private static final String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    // 私有构造函数，防止实例化
    private UUIDGenerator() {
        throw new AssertionError("不允许实例化工具类");
    }

    // ==================== 基础 UUID 生成 ====================

    /**
     * 生成标准的随机 UUID（版本4）
     * @return 标准 UUID 字符串
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成 UUID 并去除连字符
     * @return 不带连字符的 UUID 字符串
     */
    public static String randomUUIDWithoutDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 UUID 的大写形式
     * @return 大写的 UUID 字符串
     */
    public static String randomUUIDUpperCase() {
        return UUID.randomUUID().toString().toUpperCase();
    }

    /**
     * 生成 UUID 的小写形式（无连字符）
     * @return 小写无连字符的 UUID 字符串
     */
    public static String randomUUIDLowerCaseWithoutDash() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    // ==================== 高性能 UUID 生成 ====================

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Random RANDOM = new Random();

    /**
     * 使用 SecureRandom 生成安全的 UUID（加密强度更高）
     * @return 安全的 UUID 字符串
     */
    public static String secureRandomUUID() {
        byte[] uuidBytes = new byte[16];
        SECURE_RANDOM.nextBytes(uuidBytes);

        // 设置版本号为 4（随机）
        uuidBytes[6] &= 0x0f; // 清除版本位
        uuidBytes[6] |= 0x40; // 设置版本为 4

        // 设置变体位
        uuidBytes[8] &= 0x3f; // 清除变体位
        uuidBytes[8] |= 0x80; // 设置 IETF 变体

        return bytesToHex(uuidBytes);
    }

    /**
     * 使用 ThreadLocalRandom 生成高性能 UUID（适合多线程环境）
     * @return 高性能 UUID 字符串
     */
    public static String fastRandomUUID() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        byte[] uuidBytes = new byte[16];
        random.nextBytes(uuidBytes);

        // 设置版本号为 4
        uuidBytes[6] &= 0x0f;
        uuidBytes[6] |= 0x40;

        // 设置变体位
        uuidBytes[8] &= 0x3f;
        uuidBytes[8] |= 0x80;

        return bytesToHex(uuidBytes);
    }

    // ==================== 有序 UUID 生成 ====================

    private static long lastTimestamp = 0L;
    private static final Object LOCK = new Object();

    /**
     * 生成时间有序的 UUID（适合数据库主键，避免索引碎片）
     * @return 时间有序的 UUID 字符串
     */
    public static String timeOrderedUUID() {
        synchronized (LOCK) {
            long timestamp = System.currentTimeMillis();

            // 防止同一毫秒内重复
            if (timestamp <= lastTimestamp) {
                timestamp = lastTimestamp + 1;
            }
            lastTimestamp = timestamp;

            // 生成随机部分
            long randomPart = ThreadLocalRandom.current().nextLong();

            // 组合时间戳和随机数
            return String.format("%016x%016x", timestamp, randomPart);
        }
    }

    /**
     * 生成带前缀的 UUID（便于分类识别）
     * @param prefix 前缀
     * @return 带前缀的 UUID 字符串
     */
    public static String prefixedUUID(String prefix) {
        return prefix + "_" + randomUUIDWithoutDash();
    }

    /**
     * 生成指定长度的 UUID 子串
     * @param length 需要的长度（1-32）
     * @return 指定长度的 UUID 字符串
     */
    public static String randomUUIDSubstring(int length) {
        if (length < 1 || length > 32) {
            throw new IllegalArgumentException("长度必须在 1-32 之间");
        }

        String uuid = randomUUIDWithoutDash();
        return uuid.substring(0, length);
    }

    // ==================== UUID 验证和转换 ====================

    /**
     * 验证字符串是否为有效的 UUID 格式
     * @param uuid 待验证的字符串
     * @return 是否有效
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }

        // 支持带连字符和不带连字符的格式
        String normalized = uuid.replace("-", "");
        if (normalized.length() != 32) {
            return false;
        }

        return normalized.matches("[0-9a-fA-F]{32}");
    }

    /**
     * 将字符串转换为 UUID 对象
     * @param uuidStr UUID 字符串
     * @return UUID 对象
     * @throws IllegalArgumentException 如果格式无效
     */
    public static UUID toUUID(String uuidStr) {
        if (!isValidUUID(uuidStr)) {
            throw new IllegalArgumentException("无效的 UUID 格式: " + uuidStr);
        }

        // 如果包含连字符，直接使用 UUID.fromString
        if (uuidStr.contains("-")) {
            return UUID.fromString(uuidStr);
        } else {
            // 无连字符的格式需要手动插入
            String formatted = uuidStr.substring(0, 8) + "-" +
                    uuidStr.substring(8, 12) + "-" +
                    uuidStr.substring(12, 16) + "-" +
                    uuidStr.substring(16, 20) + "-" +
                    uuidStr.substring(20);
            return UUID.fromString(formatted);
        }
    }

    /**
     * 将 UUID 对象转换为指定格式的字符串
     * @param uuid UUID 对象
     * @param withDash 是否包含连字符
     * @param upperCase 是否大写
     * @return 格式化后的 UUID 字符串
     */
    public static String formatUUID(UUID uuid, boolean withDash, boolean upperCase) {
        String result = uuid.toString();
        if (!withDash) {
            result = result.replace("-", "");
        }
        return upperCase ? result.toUpperCase() : result.toLowerCase();
    }

    // ==================== 批量生成 ====================

    /**
     * 批量生成 UUID
     * @param count 生成数量
     * @return UUID 数组
     */
    public static String[] batchGenerate(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }

        String[] uuids = new String[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = randomUUID();
        }
        return uuids;
    }

    /**
     * 批量生成无连字符的 UUID
     * @param count 生成数量
     * @return UUID 数组
     */
    public static String[] batchGenerateWithoutDash(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }

        String[] uuids = new String[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = randomUUIDWithoutDash();
        }
        return uuids;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 字节数组转十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[32];
        for (int i = 0; i < 16; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 生成 UUID 并返回基本信息
     * @return 包含 UUID 和生成时间的 Info 对象
     */
    public static UUIDInfo generateWithInfo() {
        String uuid = randomUUID();
        return new UUIDInfo(uuid, System.currentTimeMillis(), UUID.fromString(uuid));
    }

    // ==================== 内部类：UUID 信息 ====================

    /**
     * UUID 信息封装类
     */
    public static class UUIDInfo {
        private final String uuid;
        private final long timestamp;
        private final UUID uuidObj;

        public UUIDInfo(String uuid, long timestamp, UUID uuidObj) {
            this.uuid = uuid;
            this.timestamp = timestamp;
            this.uuidObj = uuidObj;
        }

        public String getUuid() { return uuid; }
        public long getTimestamp() { return timestamp; }
        public UUID getUuidObj() { return uuidObj; }

        @Override
        public String toString() {
            return String.format("UUIDInfo{uuid='%s', timestamp=%d}", uuid, timestamp);
        }
    }
}