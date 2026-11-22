package org.wildfly.common;

// Shadows wildfly-common's Substitutions, whose @TargetClass points at a Graal package renamed in JDK 21+ and aborts the
// native-image build. Adapted from ArcadeDB's native module (Apache-2.0). Only branch-probability hints are dropped.
final class Substitutions {
    static final class Target_GraalDirectives {}
    static final class Target_Branch {}
    private Substitutions() {}
}
