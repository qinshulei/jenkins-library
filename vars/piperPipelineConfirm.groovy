import com.sap.piper.ConfigurationHelper
import com.sap.piper.GenerateDocumentation
import com.sap.piper.Utils
import groovy.transform.Field

import static com.sap.piper.Prerequisites.checkScript

@Field String STEP_NAME = getClass().getName()

@Field Set GENERAL_CONFIG_KEYS = [
    'manualConfirmation',
    'manualConfirmationTimeout'
]
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS

/**
 * In this stage reporting actions like mail notification or telemetry reporting are executed.
 *
 * This stage contains following steps:
 * - [influxWriteData](./influxWriteData.md)
 * - [mailSendNotification](./mailSendNotification.md)
 *
 * !!! note
 *     This stage is meant to be used in a [post](https://jenkins.io/doc/book/pipeline/syntax/#post) section of a pipeline.
 */
@GenerateDocumentation
void call(Map parameters = [:]) {
    def script = checkScript(this, parameters) ?: this
    def stageName = parameters.stageName?:env.STAGE_NAME
    // ease handling extension
    stageName = stageName.replace('Declarative: ', '')
    Map config = ConfigurationHelper.newInstance(this)
        .loadStepDefaults()
        .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
        .mixinStageConfig(script.commonPipelineEnvironment, stageName, STEP_CONFIG_KEYS)
        .mixin(parameters, PARAMETER_KEYS)
        .use()

    String unstableStepNames = cpe?.getValue('unstableSteps') ? "${cpe?.getValue('unstableSteps').stepList.join(':\n------\n')}:" : ''

    boolean approval = false
    def userInput

    timeout(
        unit: 'HOURS',
        time: config.manualConfirmationTimeout
    ){
        if (currentBuild.result == 'UNSTABLE') {
            while(!approval) {
                userInput = input(
                    message: 'Approve continuation of pipeline, although some steps failed.',
                    ok: 'Approve',
                    parameters: [
                        text(
                            defaultValue: unstableStepNames,
                            description: 'Please provide a reason for overruling following failed steps:',
                            name: 'reason'
                        ),
                        booleanParam(
                            defaultValue: false,
                            description: 'I acknowledge that the approval reason is stored together with my user name / user id:',
                            name: 'acknowledgement'
                        )
                    ]
                )
                approval = userInput.acknowledgement && userInput.reason?.length() > 10
            }
            echo "Reason:\n-------------\n${userInput.reason}"
            echo "Acknowledged:\n-------------\n${userInput.acknowledgement}"
        } else {
            input message: 'Shall we proceed to Promote & Deploy?'
        }

    }

}
