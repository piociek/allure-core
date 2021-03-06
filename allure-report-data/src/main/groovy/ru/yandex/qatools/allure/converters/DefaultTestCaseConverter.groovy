package ru.yandex.qatools.allure.converters

import com.google.inject.Inject
import org.modelmapper.Converter
import org.modelmapper.ModelMapper
import org.modelmapper.spi.MappingContext
import ru.yandex.qatools.allure.AllureAttachment
import ru.yandex.qatools.allure.plugins.ReportConfig
import ru.yandex.qatools.allure.AllureStep
import ru.yandex.qatools.allure.AllureTestCase
import ru.yandex.qatools.allure.AllureTestSuiteInfo
import ru.yandex.qatools.allure.Summary
import ru.yandex.qatools.allure.model.Attachment
import ru.yandex.qatools.allure.model.Step
import ru.yandex.qatools.allure.model.TestCaseResult
import ru.yandex.qatools.allure.plugins.AttachmentsIndex
import ru.yandex.qatools.allure.utils.PluginUtils
import ru.yandex.qatools.allure.utils.SummaryCategory
import ru.yandex.qatools.allure.utils.TextUtils

import static TextUtils.generateUid

/**
 * @author Dmitry Baev charlie@yandex-team.ru
 *         Date: 06.02.15
 */
class DefaultTestCaseConverter implements TestCaseConverter {

    public static final String UNKNOWN_STEP_NAME = "UnknownStepName"
    public static final String UNKNOWN_TEST_SUITE = "UnknownTestSuite"
    public static final String UNKNOWN_TEST_CASE = "UnknownTestCase"

    @Inject
    ReportConfig config

    @Inject
    AttachmentsIndex attachmentsIndex

    def suiteUids = [:].withDefault {
        generateUid();
    }

    @Override
    AllureTestCase convert(TestCaseResult result) {
        ModelMapper mapper = new ModelMapper()

        mapper.createTypeMap(TestCaseResult.class, AllureTestCase.class).postConverter = new TestCaseResultProcessor()
        mapper.createTypeMap(Step.class, AllureStep.class).postConverter = new StepProcessor()
        mapper.createTypeMap(Attachment.class, AllureAttachment.class).postConverter = new AttachmentProcessor()

        mapper.map(result, AllureTestCase.class);
    }

    class TestCaseResultProcessor implements Converter<TestCaseResult, AllureTestCase> {

        @Override
        public AllureTestCase convert(MappingContext<TestCaseResult, AllureTestCase> context) {
            def result = context.destination
            def source = context.source

            use([PluginUtils, SummaryCategory]) {
                result.uid = generateUid()

                result.name = source.name ?: UNKNOWN_TEST_CASE;
                result.title = result.title ?: TextUtils.humanize(result.name)

                result.description = source.convertedDescription
                result.time = source.time

                result.summary = result.steps.summary.sum(new Summary(steps: 0, attachments: 0)) as Summary
                result.summary.steps += result.steps.size()
                result.summary.attachments += result.attachments.size()

                result.severity = source.severityLevel
                result.testId = source.getTestId(config.tmsPattern)
                result.storyId = source.getStoryId(config.storyPattern)
                result.issues = source.getIssues(config.issueTrackerPattern)

                def suiteName = source.suiteName ?: UNKNOWN_TEST_SUITE;
                def suiteTitle = source.suiteTitle ?: TextUtils.humanize(suiteName);

                result.suite = new AllureTestSuiteInfo(
                        uid: suiteUids[source.suiteName],
                        name: suiteName,
                        title: suiteTitle
                );
            }

            result;
        }
    }

    class StepProcessor implements Converter<Step, AllureStep> {
        @Override
        public AllureStep convert(MappingContext<Step, AllureStep> context) {
            def result = context.destination
            def source = context.source

            use([PluginUtils, SummaryCategory]) {
                result.name = result.name ?: UNKNOWN_STEP_NAME;
                result.title = result.title ?: TextUtils.humanize(result.name)

                result.time = source.time

                result.summary = result.steps.summary.sum(new Summary(steps: 0, attachments: 0)) as Summary
                result.summary.steps += result.steps.size()
                result.summary.attachments += result.attachments.size()
            }

            result;
        }
    }

    class AttachmentProcessor implements Converter<Attachment, AllureAttachment> {
        @Override
        public AllureAttachment convert(MappingContext<Attachment, AllureAttachment> context) {
            def result = context.destination

            def info = attachmentsIndex.findBySource(result.source)

            result.uid = info?.uid ?: generateUid()
            result.size = info?.size ?: 0

            result
        }
    }
}
