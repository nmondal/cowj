##########################################
#  Runs cowj with poluglot compiler and runs graal.js fastest
#  Lots of parameters needs to be passed, so to remember here is the script
#  Build job creates the graal compiler directory at libs/graal
#  Dated Oct 28 2025
#  There is a bug that prevents it from upgrading to latest graal js on JVM 21 -- 23 is the limit for Graal binaries
#  https://github.com/oracle/graal/issues/7651
############################################

cd build/libs || exit 1

java -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI \
   --add-exports java.base/jdk.internal.misc=jdk.graal.compiler \
   --add-opens org.graalvm.truffle/com.oracle.truffle.polyglot=ALL-UNNAMED \
   --upgrade-module-path="graal" \
   -jar cowj-0.1-SNAPSHOT.jar ../../samples/graal-benchmark/graal-benchmark.yaml
