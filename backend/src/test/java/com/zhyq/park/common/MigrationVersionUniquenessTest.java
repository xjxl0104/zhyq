package com.zhyq.park.common;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移版本号唯一性守卫。
 *
 * <p>由来:重号只在应用启动时才炸(Flyway 抛 "Found more than one migration with version N"),
 * 而所有单测都不启动 Flyway,没有任何测试能拦住它。真实踩过的坑是——分支切换后把某个迁移改了名,
 * Maven 增量拷贝资源**不会删除** target/classes 下的旧文件,于是 classpath 上同时存在新旧两份,
 * 源码看着没问题、测试全绿,一启动就崩。</p>
 *
 * <p>本测试扫描的是 classpath 上真实的迁移目录(即 target/classes/db/migration),
 * 因此上面那种「源码干净但产物脏」的情况同样会被抓到,失败信息会直接点名冲突的文件。</p>
 */
class MigrationVersionUniquenessTest {

    /** Flyway 版本化迁移命名:V<版本>__<描述>.sql,版本可带点号如 V1.1。 */
    private static final Pattern VERSIONED = Pattern.compile("^V([0-9]+(?:\\.[0-9]+)*)__.+\\.sql$");

    @Test
    void everyMigrationVersionAppearsExactlyOnce() throws Exception {
        File[] files = migrationDir().listFiles();
        assertNotNull(files, "迁移目录读不到文件");

        Map<String, List<String>> byVersion = new LinkedHashMap<>();
        for (File f : files) {
            Matcher m = VERSIONED.matcher(f.getName());
            if (m.matches()) {
                byVersion.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(f.getName());
            }
        }
        assertFalse(byVersion.isEmpty(), "一个迁移都没扫到,路径可能不对");

        List<String> conflicts = byVersion.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "V" + e.getKey() + " 被这些文件同时占用: " + String.join(", ", e.getValue()))
                .toList();

        assertTrue(conflicts.isEmpty(),
                "存在重复的迁移版本号,应用启动时 Flyway 会直接拒绝:\n  "
                        + String.join("\n  ", conflicts)
                        + "\n如果源码里已经改过名,多半是 target/classes 下的旧文件没清掉,跑一次 mvn clean。");
    }

    private File migrationDir() throws Exception {
        URL url = getClass().getResource("/db/migration");
        assertNotNull(url, "classpath 上找不到 /db/migration");
        File dir = new File(url.toURI());
        assertTrue(dir.isDirectory(), "/db/migration 不是目录: " + dir);
        return dir;
    }
}
