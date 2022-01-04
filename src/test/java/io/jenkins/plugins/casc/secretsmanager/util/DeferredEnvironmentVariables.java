package io.jenkins.plugins.casc.secretsmanager.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DeferredEnvironmentVariables implements TestRule {

    private final Map<String, Supplier<String>> buffer = new HashMap<>();

    /**
     * Specify an env var whose value is only available later on (e.g. after another JUnit test resource has finished
     * setting up).
     */
    public DeferredEnvironmentVariables set(String name, Supplier<String> value) {
        buffer.put(name, value);
        return this;
    }

    /**
     * Specify an env var whose value is immediately available.
     */
    public DeferredEnvironmentVariables set(String name, String value) {
        this.buffer.put(name, () -> value);
        return this;
    }

    private void writeVariableToEnvMap(String name, String value) {
        this.set(getEditableMapOfVariables(), name, value);
        this.set(getTheCaseInsensitiveEnvironment(), name, value);
    }

    private void set(Map<String, String> variables, String name, String value) {
        if (variables != null) {
            if (value == null) {
                variables.remove(name);
            } else {
                variables.put(name, value);
            }
        }

    }

    private void copyVariablesFromBufferToEnvMap() {
        for (Map.Entry<String, Supplier<String>> stringSupplierEntry : this.buffer.entrySet()) {
            final String name = stringSupplierEntry.getKey();
            final String value = stringSupplierEntry.getValue().get();
            this.writeVariableToEnvMap(name, value);
        }
    }

    public Statement apply(Statement base, Description description) {
        return new DeferredEnvironmentVariables.EnvironmentVariablesStatement(base);
    }

    private static Map<String, String> getEditableMapOfVariables() {
        Class classOfMap = System.getenv().getClass();

        try {
            return getFieldValue(classOfMap, System.getenv(), "m");
        } catch (IllegalAccessException var2) {
            throw new RuntimeException("System Rules cannot access the field 'm' of the map System.getenv().", var2);
        } catch (NoSuchFieldException var3) {
            throw new RuntimeException("System Rules expects System.getenv() to have a field 'm' but it has not.", var3);
        }
    }

    private static Map<String, String> getTheCaseInsensitiveEnvironment() {
        try {
            Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
            return getFieldValue(processEnvironment, (Object)null, "theCaseInsensitiveEnvironment");
        } catch (ClassNotFoundException var1) {
            throw new RuntimeException("System Rules expects the existence of the class java.lang.ProcessEnvironment but it does not exist.", var1);
        } catch (IllegalAccessException var2) {
            throw new RuntimeException("System Rules cannot access the static field 'theCaseInsensitiveEnvironment' of the class java.lang.ProcessEnvironment.", var2);
        } catch (NoSuchFieldException var3) {
            return null;
        }
    }

    private static Map<String, String> getFieldValue(Class<?> klass, Object object, String name) throws NoSuchFieldException, IllegalAccessException {
        Field field = klass.getDeclaredField(name);
        field.setAccessible(true);
        return (Map)field.get(object);
    }

    private class EnvironmentVariablesStatement extends Statement {
        final Statement baseStatement;
        Map<String, String> originalVariables;

        EnvironmentVariablesStatement(Statement baseStatement) {
            this.baseStatement = baseStatement;
        }

        public void evaluate() throws Throwable {
            this.saveCurrentState();

            try {
                DeferredEnvironmentVariables.this.copyVariablesFromBufferToEnvMap();
                this.baseStatement.evaluate();
            } finally {
                this.restoreOriginalVariables();
            }

        }

        void saveCurrentState() {
            this.originalVariables = new HashMap<>(System.getenv());
        }

        void restoreOriginalVariables() {
            this.restoreVariables(DeferredEnvironmentVariables.getEditableMapOfVariables());
            Map<String, String> theCaseInsensitiveEnvironment = DeferredEnvironmentVariables.getTheCaseInsensitiveEnvironment();
            if (theCaseInsensitiveEnvironment != null) {
                this.restoreVariables(theCaseInsensitiveEnvironment);
            }

        }

        void restoreVariables(Map<String, String> variables) {
            variables.clear();
            variables.putAll(this.originalVariables);
        }
    }


}
