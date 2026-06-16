package com.wornux.usecases.uc013_github_ci_cd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class UC013GithubCiCdTest {

    private static final Path WORKFLOWS = Path.of(".github/workflows");
    private static final Path SPEC = Path.of("spec/use-cases/use-case-013-github-ci-cd.md");
    private static final Path MAVEN_WRAPPER = Path.of(".mvn/wrapper/maven-wrapper.properties");

    @Test
    void mainFlow_pullRequestsRunTestsWithJacocoAndMergesPublishDockerImage() throws IOException {
        String unitTests = read(WORKFLOWS.resolve("unit-tests.yml"));
        String integrationTests = read(WORKFLOWS.resolve("integration-tests.yml"));
        String dockerPublish = read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(unitTests)
                .contains("name: Unit Tests")
                .contains("pull_request:")
                .contains("./mvnw -B test jacoco:report")
                .contains("actions/upload-artifact@v4")
                .contains("target/site/jacoco");
        assertThat(integrationTests)
                .contains("name: Integration Tests")
                .contains("pull_request:")
                .contains("./mvnw -B verify")
                .contains("actions/upload-artifact@v4")
                .contains("target/site/jacoco");
        assertThat(dockerPublish)
                .contains("name: Docker Publish")
                .contains("push:")
                .contains("wornux/qa-final-project")
                .contains("docker/build-push-action@v6")
                .contains("push: true");
    }

    @Test
    void af1_unitTestFailuresProduceFailedChecks() throws IOException {
        String unitTests = read(WORKFLOWS.resolve("unit-tests.yml"));

        assertThat(unitTests)
                .contains("Set up Java")
                .contains("Run unit tests with JaCoCo")
                .doesNotContain("continue-on-error: true");
    }

    @Test
    void af2_integrationTestFailuresProduceFailedChecks() throws IOException {
        String integrationTests = read(WORKFLOWS.resolve("integration-tests.yml"));

        assertThat(integrationTests)
                .contains("Run integration tests with JaCoCo")
                .doesNotContain("continue-on-error: true");
    }

    @Test
    void af3_andAf4_dockerBuildOrPublishFailuresProduceFailedChecks() throws IOException {
        String dockerPublish = read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(dockerPublish)
                .contains("docker/login-action@v3")
                .contains("docker/build-push-action@v6")
                .doesNotContain("continue-on-error: true");
    }

    @Test
    void af5_nonPublishableBranchesDoNotTriggerDockerPublish() throws IOException {
        String dockerPublish = read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(dockerPublish)
                .contains("branches: [main, staging, development]")
                .doesNotContain("pull_request:");
    }

    @Test
    void br01_exactlyThreeGithubWorkflowsAreDefined() throws IOException {
        List<String> workflowNames;
        try (var stream = Files.list(WORKFLOWS)) {
            workflowNames = stream
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertThat(workflowNames).containsExactly(
                "docker-publish.yml",
                "integration-tests.yml",
                "unit-tests.yml");
    }

    @Test
    void br02AndBr03_pullRequestTestWorkflowsRunOnOpenedReopenedSynchronizeAndReadyForReview() throws IOException {
        assertPullRequestEvents(read(WORKFLOWS.resolve("unit-tests.yml")));
        assertPullRequestEvents(read(WORKFLOWS.resolve("integration-tests.yml")));
    }

    @Test
    void br04AndBr05_testWorkflowsRunJacocoAndUploadCoverageArtifacts() throws IOException {
        assertJacocoArtifact(
                read(WORKFLOWS.resolve("unit-tests.yml")),
                "./mvnw -B test jacoco:report",
                "unit-tests-jacoco-report");
        assertJacocoArtifact(
                read(WORKFLOWS.resolve("integration-tests.yml")),
                "./mvnw -B verify",
                "integration-tests-jacoco-report");
    }

    @Test
    void br06AndBr09_dockerPublishRunsOnlyForPublishBranchesIncludingStagingAndDevelopment() throws IOException {
        String dockerPublish = read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(dockerPublish)
                .contains("branches: [main, staging, development]")
                .contains("type=ref,event=branch")
                .contains("type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}");
    }

    @Test
    void br07_dockerImageUsesWornuxNamespace() throws IOException {
        assertThat(read(WORKFLOWS.resolve("docker-publish.yml")))
                .contains("images: wornux/qa-final-project");
    }

    @Test
    void br08AndBr10_dockerCredentialsUseSecretsAndDoNotHardcodeTokens() throws IOException {
        String dockerPublish = read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(dockerPublish)
                .contains("username: ${{ secrets.DOCKERHUB_USERNAME }}")
                .contains("password: ${{ secrets.DOCKERHUB_TOKEN }}")
                .doesNotContain("password123")
                .doesNotContain("token:");
    }

    @Test
    void br11_workflowFilesLiveUnderGithubWorkflowsAndMavenWrapperIsUsable() throws IOException {
        assertThat(WORKFLOWS.resolve("unit-tests.yml")).exists();
        assertThat(WORKFLOWS.resolve("integration-tests.yml")).exists();
        assertThat(WORKFLOWS.resolve("docker-publish.yml")).exists();
        assertThat(read(MAVEN_WRAPPER)).contains("distributionUrl=");
    }

    @Test
    void br12_failedTestsCoverageUploadOrDockerPublishFailTheWorkflow() throws IOException {
        String allWorkflows = read(WORKFLOWS.resolve("unit-tests.yml"))
                + read(WORKFLOWS.resolve("integration-tests.yml"))
                + read(WORKFLOWS.resolve("docker-publish.yml"));

        assertThat(allWorkflows)
                .contains("if-no-files-found: error")
                .contains("push: true")
                .doesNotContain("continue-on-error: true");
    }

    @Test
    void specIsMarkedImplementedAfterWorkflowCoverage() throws IOException {
        assertThat(read(SPEC))
                .contains("**Status:** Implemented")
                .contains("- [x] Main Flow covered (steps 1-12)")
                .contains("- [x] AF-1, AF-2, AF-3, AF-4, AF-5 covered")
                .contains("- [x] BR-01 through BR-12 covered");
    }

    private void assertPullRequestEvents(String workflow) {
        assertThat(workflow)
                .contains("pull_request:")
                .contains("types: [opened, reopened, synchronize, ready_for_review]");
    }

    private void assertJacocoArtifact(String workflow, String testCommand, String artifactName) {
        assertThat(workflow)
                .contains(testCommand)
                .contains("actions/upload-artifact@v4")
                .contains("name: " + artifactName)
                .contains("path: target/site/jacoco")
                .contains("if-no-files-found: error");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
