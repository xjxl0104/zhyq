---
name: zhyq-build-env
description: "zhyq 后端本机编译环境 — mvn/java 不在 PATH,需手动指路径"
metadata:
  node_type: memory
  type: reference
---

本机(Mac, ~/Documents/zhyq)编译 zhyq 后端:`mvn` 和 `java` **默认都不在 PATH**。可用姿势:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
MVN=/opt/homebrew/var/homebrew/tmp/.cellar/maven/3.9.16/libexec/bin/mvn
"$MVN" -q -o compile     # -o 离线,跳过慢仓库检查;离线编译可过
"$MVN" -o test -Dtest=XxxTest   # 单测(纯单元,别加 -q 否则结果也被吞)
```

**坑**:
- backend 下**无 `./mvnw` wrapper**。
- `/opt/homebrew/bin/mvn` 是未 link 的坏模板(路径里含 `@@HOMEBREW_CELLAR@@`),**别用**,用上面 libexec 全路径。
- openjdk@17 已装但未 link。
- mvn 必须在 `backend/` 目录跑(见 [[zhyq-project]])。
- 全量 `mvn test` 里的集成测试需要 MySQL;无 DB 时只能跑纯单元测试(如 JwtServiceTest)。

**生成 BCrypt 哈希**(无需起 Spring,借 .m2 现成 jar):
spring-security-crypto + spring-jcl 两个 jar 上 classpath,new BCryptPasswordEncoder().encode(明文)。
