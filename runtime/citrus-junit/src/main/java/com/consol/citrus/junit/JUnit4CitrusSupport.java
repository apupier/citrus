/*
 * Copyright 2006-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.junit;

import java.lang.annotation.Annotation;
import java.util.Date;

import com.consol.citrus.Citrus;
import com.consol.citrus.DefaultTestCase;
import com.consol.citrus.DefaultTestCaseRunner;
import com.consol.citrus.GherkinTestActionRunner;
import com.consol.citrus.TestAction;
import com.consol.citrus.TestActionBuilder;
import com.consol.citrus.TestActionRunner;
import com.consol.citrus.TestBehavior;
import com.consol.citrus.TestCase;
import com.consol.citrus.TestCaseMetaInfo;
import com.consol.citrus.TestCaseRunner;
import com.consol.citrus.TestResult;
import com.consol.citrus.annotations.CitrusAnnotations;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.annotations.CitrusXmlTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.exceptions.TestCaseFailedException;
import org.junit.runner.RunWith;
import org.springframework.util.ReflectionUtils;

/**
 * @author Christoph Deppisch
 * @since 2.5
 */
@RunWith(CitrusJUnit4Runner.class)
public class JUnit4CitrusSupport implements GherkinTestActionRunner {

    private static final String BUILDER_ATTRIBUTE = "builder";

    /** Citrus instance */
    protected Citrus citrus;

    /** Test builder delegate */
    private TestCaseRunner testCaseRunner;

    protected void run(CitrusFrameworkMethod frameworkMethod) {
        if (citrus == null) {
            citrus = Citrus.newInstance();
        }

        TestContext ctx = prepareTestContext(citrus.getCitrusContext().createTestContext());

        if (frameworkMethod.getMethod().getAnnotation(CitrusTest.class) != null) {
            TestCaseRunner testCaseBuilder = createTestRunner(frameworkMethod, ctx);
            frameworkMethod.setAttribute(BUILDER_ATTRIBUTE, testCaseBuilder);
            CitrusAnnotations.injectAll(this, citrus, ctx);

            invokeTestMethod(frameworkMethod, testCaseBuilder, ctx);
        } else if (frameworkMethod.getMethod().getAnnotation(CitrusXmlTest.class) != null) {
            throw new CitrusRuntimeException("Unsupported XML test annotation - please add Spring support");
        }
    }

    /**
     * Invokes test method based on designer or runner environment.
     * @param frameworkMethod
     * @param testCaseBuilder
     * @param context
     */
    protected void invokeTestMethod(CitrusFrameworkMethod frameworkMethod, TestCaseRunner testCaseBuilder, TestContext context) {
        final TestCase testCase = testCaseBuilder.getTestCase();
        try {
            Object[] params = resolveParameter(frameworkMethod, testCase, context);
            testCaseBuilder.start();
            ReflectionUtils.invokeMethod(frameworkMethod.getMethod(), this, params);
        } catch (Exception | AssertionError e) {
            testCase.setTestResult(TestResult.failed(testCase.getName(), testCase.getTestClass().getName(), e));
            throw new TestCaseFailedException(e);
        } finally {
            testCaseBuilder.stop();
        }
    }

    /**
     * Resolves value for annotated method parameter.
     *
     * @param frameworkMethod
     * @param parameterType
     * @return
     */
    protected Object resolveAnnotatedResource(CitrusFrameworkMethod frameworkMethod, Class<?> parameterType, TestContext context) {
        Object storedBuilder = frameworkMethod.getAttribute(BUILDER_ATTRIBUTE);
        if (TestCaseRunner.class.isAssignableFrom(parameterType)) {
            return storedBuilder;
        } else if (TestActionRunner.class.isAssignableFrom(parameterType)
                && storedBuilder instanceof TestActionRunner) {
            return storedBuilder;
        } else if (GherkinTestActionRunner.class.isAssignableFrom(parameterType)
                && storedBuilder instanceof GherkinTestActionRunner) {
            return storedBuilder;
        } else if (TestContext.class.isAssignableFrom(parameterType)) {
            return context;
        } else {
            throw new CitrusRuntimeException("Not able to provide a Citrus resource injection for type " + parameterType);
        }
    }

    /**
     * Resolves method arguments supporting TestNG data provider parameters as well as
     * {@link CitrusResource} annotated methods.
     *
     * @param frameworkMethod
     * @param testCase
     * @param context
     * @return
     */
    protected Object[] resolveParameter(CitrusFrameworkMethod frameworkMethod, TestCase testCase, TestContext context) {
        Object[] values = new Object[frameworkMethod.getMethod().getParameterTypes().length];
        Class<?>[] parameterTypes = frameworkMethod.getMethod().getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            final Annotation[] parameterAnnotations = frameworkMethod.getMethod().getParameterAnnotations()[i];
            Class<?> parameterType = parameterTypes[i];
            for (Annotation annotation : parameterAnnotations) {
                if (annotation instanceof CitrusResource) {
                    values[i] = resolveAnnotatedResource(frameworkMethod, parameterType, context);
                }
            }
        }

        return values;
    }

    /**
     * Prepares the test context.
     *
     * Provides a hook for test context modifications before the test gets executed.
     *
     * @param testContext the test context.
     * @return the (prepared) test context.
     */
    protected TestContext prepareTestContext(final TestContext testContext) {
        return testContext;
    }

    /**
     * Creates new test runner instance for this test method.
     * @param frameworkMethod
     * @param context
     * @return
     */
    protected TestCaseRunner createTestRunner(CitrusFrameworkMethod frameworkMethod, TestContext context) {
        testCaseRunner = new DefaultTestCaseRunner(new DefaultTestCase(), context);
        testCaseRunner.testClass(this.getClass());
        testCaseRunner.name(frameworkMethod.getTestName());
        testCaseRunner.packageName(frameworkMethod.getPackageName());

        return testCaseRunner;
    }

    @Override
    public <T extends TestAction> T run(TestActionBuilder<T> builder) {
        return testCaseRunner.run(builder);
    }

    @Override
    public <T extends TestAction> TestActionBuilder<T> applyBehavior(TestBehavior behavior) {
        return testCaseRunner.applyBehavior(behavior);
    }

    public <T> T variable(String name, T value) {
        return testCaseRunner.variable(name, value);
    }

    public void name(String name) {
        testCaseRunner.name(name);
    }

    public void description(String description) {
        testCaseRunner.description(description);
    }

    public void author(String author) {
        testCaseRunner.author(author);
    }

    public void status(TestCaseMetaInfo.Status status) {
        testCaseRunner.status(status);
    }

    public void creationDate(Date date) {
        testCaseRunner.creationDate(date);
    }
}
