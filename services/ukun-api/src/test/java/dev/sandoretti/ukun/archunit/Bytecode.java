package dev.sandoretti.ukun.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The little bytecode reading that three of the seven rules of {@code docs/03-analisis-y-patrones.md}
 * §3 need and ArchUnit cannot give them.
 *
 * <p>Two of those rules are about things that are simply not in ArchUnit's model. {@code lombok.Data}
 * has {@code SOURCE} retention, so no tool that reads class files can ever see it — what is visible
 * is {@code @lombok.Generated} on the methods it wrote, and only because {@code services/lombok.config}
 * asks Lombok to stamp it. And a record's implicit {@code toString()} is indistinguishable from a
 * hand-written one by signature alone: what tells them apart is that the compiler's version is an
 * {@code invokedynamic} to {@code java.lang.runtime.ObjectMethods}.
 *
 * <p>It reads class files with {@link ClassFile}, the JDK's own Class-File API (final since JDK 24,
 * so plain standard library on the Java 25 of {@code docs/05-stack-y-versiones.md}). No ASM, no
 * further dependency.
 */
final class Bytecode {

    private static final ClassDesc LOMBOK_GENERATED = ClassDesc.of("lombok.Generated");
    private static final ClassDesc OBJECT_METHODS = ClassDesc.of("java.lang.runtime.ObjectMethods");
    private static final String TO_STRING_DESCRIPTOR = "()Ljava/lang/String;";

    private Bytecode() {}

    /**
     * Empty when the class file is not on the classpath, which for the classes ArchUnit just
     * imported should not happen — the rules treat it as "nothing to say" rather than failing.
     */
    static Optional<ClassModel> parse(JavaClass javaClass) {
        String resource = javaClass.getName().replace('.', '/') + ".class";

        try (InputStream bytes = Bytecode.class.getClassLoader().getResourceAsStream(resource)) {
            return bytes == null ? Optional.empty() : Optional.of(ClassFile.of().parse(bytes.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the class file of " + javaClass.getName(), e);
        }
    }

    /** Methods Lombok wrote, told apart by the annotation {@code services/lombok.config} enables. */
    static boolean isLombokGenerated(MethodModel method) {
        return method.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .map(attribute -> attribute.annotations())
                .orElse(List.of())
                .stream()
                .anyMatch(annotation -> LOMBOK_GENERATED.equals(annotation.classSymbol()));
    }

    /**
     * A {@code toString()} somebody actually wrote, as opposed to one the compiler or Lombok
     * produced. The distinction is the whole point of the sixth rule: a {@code record} always
     * carries a {@code toString()} in its class file, and that one prints every component — which
     * is exactly the leak {@code docs/11-observabilidad-y-logging.md} §4 is trying to prevent.
     */
    static boolean declaresHandWrittenToString(ClassModel model) {
        return model.methods().stream()
                .filter(method -> "toString".equals(method.methodName().stringValue()))
                .filter(method -> TO_STRING_DESCRIPTOR.equals(method.methodType().stringValue()))
                .anyMatch(method -> !isLombokGenerated(method) && !isCompilerGeneratedRecordToString(method));
    }

    private static boolean isCompilerGeneratedRecordToString(MethodModel method) {
        return method.code()
                .map(code -> code.elementStream()
                        .anyMatch(element -> element instanceof InvokeDynamicInstruction indy
                                && OBJECT_METHODS.equals(bootstrapOwnerOf(indy))))
                .orElse(false);
    }

    /** The class that owns the bootstrap method of an {@code invokedynamic} call site. */
    static ClassDesc bootstrapOwnerOf(InvokeDynamicInstruction indy) {
        return indy.invokedynamic()
                .bootstrap()
                .bootstrapMethod()
                .reference()
                .owner()
                .asSymbol();
    }

    /**
     * Slot to declared type, read from the debug information {@code maven-compiler-plugin} emits by
     * default. Without it an {@code ALOAD} carries no type and the logging rule would go blind, so
     * the rule says so out loud rather than passing quietly.
     *
     * <p>Scopes are flattened: a slot reused by two variables of different types in disjoint ranges
     * keeps the last one seen. It costs nothing here, where the lookup only ever happens for the
     * arguments of a call a few instructions away from the load.
     */
    static Map<Integer, ClassDesc> localVariableTypes(CodeModel code) {
        Map<Integer, ClassDesc> types = new HashMap<>();

        code.elementStream()
                .filter(LocalVariable.class::isInstance)
                .map(LocalVariable.class::cast)
                .forEach(variable -> types.put(variable.slot(), variable.typeSymbol()));

        return types;
    }
}
