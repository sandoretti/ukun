package dev.sandoretti.ukun.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seventh rule of {@code docs/03-analisis-y-patrones.md} §3: no production method passes an
 * {@code @Entity} as an argument to {@code log.*(...)}. It is the rule
 * {@code docs/11-observabilidad-y-logging.md} §4 values most — it would have caught defects #10,
 * #20a and #20b before they reached {@code main}.
 *
 * <h2>Why this is not an ArchUnit one-liner</h2>
 *
 * ArchUnit models a method call by its <em>declared</em> signature, and every logging method of
 * slf4j declares {@code Object}. Nothing in {@code Logger.info(String, Object...)} says what was
 * actually handed over, so no combination of {@code noClasses().that()...} can express this rule.
 * What does say it is the bytecode at the call site, read here with the JDK's Class-File API
 * (see {@link Bytecode}).
 *
 * <h2>How it reads it</h2>
 *
 * A miniature abstract interpreter walks each method and tracks, for every value on the operand
 * stack, its type and whether an entity contributed to it. At a call to {@code Logger}, the values
 * about to become its arguments are the ones on top of the stack — no guessing about which loads
 * "look like" arguments. Three shapes reach it, and all three are covered:
 *
 * <ul>
 *   <li>{@code log.info(order)} — the entity is the argument.
 *   <li>{@code log.info("order {}", order)} — the entity is stored into the varargs array, so the
 *       array carries the taint to the call.
 *   <li>{@code log.info("order " + order)} — {@code invokedynamic} on {@code StringConcatFactory},
 *       whose result inherits the taint of its parts.
 * </ul>
 *
 * Taint deliberately does <strong>not</strong> propagate through an ordinary call:
 * {@code log.info("order {}", order.getId())} is exactly what the code is supposed to do, and the
 * entity there is consumed as the receiver of {@code getId()} rather than logged. The one exception
 * is a call that returns a {@code String} from tainted input, which covers
 * {@code String.valueOf(order)} and {@code order.toString()}.
 *
 * <p>Where the interpreter runs out of depth — a branch, arithmetic, an unmodelled opcode — it
 * clears the stack rather than guessing. The cost is a possible miss on a contrived expression; the
 * benefit is that it never invents a violation. The three shapes above, which are the ones real
 * code writes, are pinned by {@code ArchitectureRulesSelfTest}.
 *
 * <p>It depends on the local variable table for the type behind an {@code ALOAD}. The convention
 * plugin in {@code buildSrc} sets {@code options.isDebug} explicitly for that reason; if anyone ever
 * turns it off, this rule would quietly weaken rather than fail.
 */
class EntityLoggingCondition extends ArchCondition<JavaClass> {

    private static final String LOGGER = "org.slf4j.Logger";
    private static final Set<String> LOG_METHODS = Set.of("trace", "debug", "info", "warn", "error");
    private static final ClassDesc STRING_CONCAT_FACTORY = ClassDesc.of("java.lang.invoke.StringConcatFactory");
    private static final Set<String> TEXT_BUILDERS = Set.of("java.lang.StringBuilder", "java.lang.StringBuffer");

    private Set<String> entityNames = Set.of();

    EntityLoggingCondition() {
        super("never pass an @Entity as an argument to log.*(...)");
    }

    @Override
    public void init(Collection<JavaClass> allClasses) {
        entityNames = allClasses.stream()
                .filter(candidate -> candidate.isAnnotatedWith(Entity.class))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents events) {
        if (entityNames.isEmpty()) {
            return;
        }

        Bytecode.parse(javaClass)
                .ifPresent(model -> model.methods().forEach(method -> checkMethod(javaClass, method, events)));
    }

    private void checkMethod(JavaClass javaClass, MethodModel method, ConditionEvents events) {
        method.code().ifPresent(code -> {
            Map<Integer, ClassDesc> locals = Bytecode.localVariableTypes(code);
            Deque<Value> stack = new ArrayDeque<>();

            for (CodeElement element : code) {
                interpret(element, stack, locals, javaClass, method, events);
            }
        });
    }

    private void interpret(
            CodeElement element,
            Deque<Value> stack,
            Map<Integer, ClassDesc> locals,
            JavaClass javaClass,
            MethodModel method,
            ConditionEvents events) {

        switch (element) {
            case LoadInstruction load -> stack.push(valueOf(locals.get(load.slot())));
            case StoreInstruction ignored -> pop(stack, 1);
            case ConstantInstruction ignored -> stack.push(Value.unknown());
            case NopInstruction ignored -> {}
            case IncrementInstruction ignored -> {}
            case PseudoInstruction ignored -> {}

            case FieldInstruction field -> interpretFieldAccess(field, stack);
            case InvokeInstruction invoke -> interpretInvocation(invoke, stack, javaClass, method, events);
            case InvokeDynamicInstruction indy -> interpretCallSite(indy, stack);

            case NewObjectInstruction created -> stack.push(valueOf(created.className().asSymbol()));
            case NewReferenceArrayInstruction created -> {
                pop(stack, 1);
                stack.push(valueOf(created.componentType().asSymbol().arrayType()));
            }
            case NewPrimitiveArrayInstruction ignored -> {
                pop(stack, 1);
                stack.push(Value.unknown());
            }

            case ArrayStoreInstruction ignored -> interpretArrayStore(stack);
            case ArrayLoadInstruction ignored -> {
                pop(stack, 2);
                stack.push(Value.unknown());
            }

            case TypeCheckInstruction check -> interpretTypeCheck(check, stack);
            case StackInstruction stackOperation -> interpretStackOperation(stackOperation, stack);

            default -> stack.clear();
        }
    }

    private void interpretFieldAccess(FieldInstruction field, Deque<Value> stack) {
        switch (field.opcode()) {
            case GETSTATIC -> stack.push(valueOf(field.typeSymbol()));
            case GETFIELD -> {
                pop(stack, 1);
                stack.push(valueOf(field.typeSymbol()));
            }
            case PUTSTATIC -> pop(stack, 1);
            case PUTFIELD -> pop(stack, 2);
            default -> stack.clear();
        }
    }

    private void interpretInvocation(
            InvokeInstruction invoke,
            Deque<Value> stack,
            JavaClass javaClass,
            MethodModel method,
            ConditionEvents events) {

        MethodTypeDesc signature = invoke.typeSymbol();
        int receivers = invoke.opcode() == Opcode.INVOKESTATIC ? 0 : 1;
        List<Value> arguments = pop(stack, signature.parameterCount() + receivers);

        if (isLoggerCall(invoke)) {
            arguments.stream()
                    .filter(Value::isTainted)
                    .findFirst()
                    .ifPresent(leak -> events.add(SimpleConditionEvent.violated(
                            javaClass,
                            "%s.%s passes the entity %s to log.%s(...) — docs/11-observabilidad-y-logging.md §4"
                                    .formatted(
                                            javaClass.getName(),
                                            method.methodName().stringValue(),
                                            leak.leakedEntity(),
                                            invoke.name().stringValue()))));
        }

        push(stack, signature.returnType(), stringifiesTaintedInput(invoke, arguments), leakedEntityOf(arguments));
    }

    /**
     * The short list of calls that turn an entity into text and therefore carry the leak forward:
     * {@code order.toString()}, {@code String.valueOf(order)}, {@code Objects.toString(order)},
     * {@code String.format(...)} and a {@code StringBuilder} chain.
     *
     * <p>Everything else drops the taint, and that is the point. {@code log.info("product {}",
     * product.getName())} is what the code is supposed to do — a name pulled out of an entity is
     * not the entity, and a rule that reported it would be turned off within a week.
     */
    private boolean stringifiesTaintedInput(InvokeInstruction invoke, List<Value> arguments) {
        String owner = binaryNameOf(invoke.owner().asSymbol());
        String name = invoke.name().stringValue();

        boolean stringifies = "toString".equals(name)
                || ("java.lang.String".equals(owner) && ("valueOf".equals(name) || "format".equals(name)))
                || (TEXT_BUILDERS.contains(owner) && "append".equals(name));

        return stringifies && arguments.stream().anyMatch(Value::isTainted);
    }

    private void interpretCallSite(InvokeDynamicInstruction indy, Deque<Value> stack) {
        MethodTypeDesc signature = indy.typeSymbol();
        List<Value> arguments = pop(stack, signature.parameterCount());

        // Only string concatenation carries its parts forward. A lambda capturing an entity is a
        // different question, and answering it here would report code that never logs anything.
        boolean concatenatesTaintedParts = STRING_CONCAT_FACTORY.equals(Bytecode.bootstrapOwnerOf(indy))
                && arguments.stream().anyMatch(Value::isTainted);

        push(stack, signature.returnType(), concatenatesTaintedParts, leakedEntityOf(arguments));
    }

    /** {@code AASTORE} pops value, index and array reference — a tainted value taints the array. */
    private void interpretArrayStore(Deque<Value> stack) {
        List<Value> popped = pop(stack, 3);
        Value value = popped.get(0);
        Value array = popped.get(2);

        if (value.isTainted()) {
            array.taintWith(value.leakedEntity());
        }
    }

    private void interpretTypeCheck(TypeCheckInstruction check, Deque<Value> stack) {
        Value operand = pop(stack, 1).getFirst();

        if (check.opcode() == Opcode.CHECKCAST) {
            stack.push(new Value(check.type().asSymbol(), operand.isTainted(), operand.leakedEntity()));
        } else {
            stack.push(Value.unknown());
        }
    }

    private void interpretStackOperation(StackInstruction operation, Deque<Value> stack) {
        switch (operation.opcode()) {
            // The same instance on purpose: this is how the varargs array keeps the taint that
            // AASTORE puts on the copy underneath it.
            case DUP -> stack.push(stack.isEmpty() ? Value.unknown() : stack.peek());
            case POP -> pop(stack, 1);
            default -> stack.clear();
        }
    }

    private boolean isLoggerCall(InvokeInstruction invoke) {
        return LOGGER.equals(binaryNameOf(invoke.owner().asSymbol()))
                && LOG_METHODS.contains(invoke.name().stringValue());
    }

    private void push(Deque<Value> stack, ClassDesc type, boolean tainted, String leakedEntity) {
        if (ConstantDescs.CD_void.equals(type)) {
            return;
        }
        Value value = valueOf(type);
        if (tainted) {
            value.taintWith(leakedEntity);
        }
        stack.push(value);
    }

    private Value valueOf(ClassDesc type) {
        String name = binaryNameOf(type);
        return entityNames.contains(name) ? new Value(type, true, name) : new Value(type, false, null);
    }

    private static String leakedEntityOf(List<Value> values) {
        return values.stream()
                .filter(Value::isTainted)
                .map(Value::leakedEntity)
                .findFirst()
                .orElse(null);
    }

    private static String binaryNameOf(ClassDesc type) {
        if (type == null || !type.isClassOrInterface()) {
            return "";
        }
        String descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    /**
     * Pops {@code count} values, most recent first. An empty stack yields unknowns rather than
     * throwing: the interpreter clears the stack whenever it loses track, so asking for more than
     * it holds is expected.
     */
    private static List<Value> pop(Deque<Value> stack, int count) {
        List<Value> popped = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            popped.add(stack.isEmpty() ? Value.unknown() : stack.pop());
        }
        return popped;
    }

    /**
     * A value on the abstract stack. Mutable and shared by reference on purpose — {@code DUP}
     * pushes the same instance, which is what lets {@code AASTORE} taint the varargs array that the
     * logging call is about to receive.
     */
    private static final class Value {

        private final ClassDesc type;
        private boolean tainted;
        private String leakedEntity;

        private Value(ClassDesc type, boolean tainted, String leakedEntity) {
            this.type = type;
            this.tainted = tainted;
            this.leakedEntity = leakedEntity;
        }

        static Value unknown() {
            return new Value(null, false, null);
        }

        boolean isTainted() {
            return tainted;
        }

        String leakedEntity() {
            return leakedEntity;
        }

        void taintWith(String entity) {
            this.tainted = true;
            this.leakedEntity = entity;
        }

        @Override
        public String toString() {
            return "Value[type=" + type + ", tainted=" + tainted + "]";
        }
    }
}
