package org.sonar.plugins.stash;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.sonar.api.rule.Severity;
import org.sonar.plugins.stash.client.StashClient;
import org.sonar.plugins.stash.client.StashCredentials;
import org.sonar.plugins.stash.exceptions.StashClientException;
import org.sonar.plugins.stash.exceptions.StashConfigurationException;
import org.sonar.plugins.stash.issue.MarkdownPrinter;
import org.sonar.plugins.stash.issue.SonarQubeIssue;
import org.sonar.plugins.stash.issue.SonarQubeIssuesReport;
import org.sonar.plugins.stash.issue.StashCommentReport;
import org.sonar.plugins.stash.issue.StashDiffReport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.sonar.plugins.stash.issue.StashPullRequest;
import org.sonar.plugins.stash.issue.StashUser;

public class StashRequestFacadeTest {

  StashRequestFacade myFacade;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  StashPluginConfiguration config;

  @Mock
  StashClient stashClient;

  @Mock
  StashDiffReport diffReport;

  @Mock
  StashCommentReport stashComments1;

  @Mock
  StashCommentReport stashComments2;

  String stashCommentMessage1;
  String stashCommentMessage2;
  String stashCommentMessage3;

  SonarQubeIssuesReport issueReport;

  private static final String STASH_PROJECT = "Project";
  private static final String STASH_REPOSITORY = "Repository";
  private static final String STASH_PULLREQUEST_ID = "1";
  private static final String STASH_DIFF_TYPE = "StashDiffType";
  private static final String STASH_USER = "SonarQube";
  private static final String SONARQUBE_URL = "http://sonar/url";

  private static final String FILE_PATH_1 = "path/to/file1";
  private static final String FILE_PATH_2 = "path/to/file2";
  private static final long COMMENT_ID = 100L;

  @Before
  public void setUp() {
    config = mock(StashPluginConfiguration.class);
    myFacade = new StashRequestFacade(config);
  
    stashClient = mock(StashClient.class);
  }

  private void initConfigForPostCommentLineOnPullRequest() throws Exception {
    stashClient = mock(StashClient.class);

    diffReport = mock(StashDiffReport.class);
    when(diffReport.getType(anyString(), anyLong())).thenReturn(STASH_DIFF_TYPE);
    when(diffReport.getLine(FILE_PATH_1, 1)).thenReturn((long) 1);
    when(diffReport.getLine(FILE_PATH_1, 2)).thenReturn((long) 2);
    when(diffReport.getLine(FILE_PATH_2, 1)).thenReturn((long) 1);
    when(diffReport.getPath(FILE_PATH_1)).thenReturn(FILE_PATH_1);
    when(diffReport.getPath(FILE_PATH_2)).thenReturn(FILE_PATH_2);
    when(stashClient.getPullRequestDiffs(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID)).thenReturn(diffReport);

    issueReport = new SonarQubeIssuesReport();

    SonarQubeIssue issue1 = new SonarQubeIssue(Severity.MAJOR, "message1", "rule1", FILE_PATH_1, 1);
    stashCommentMessage1 = MarkdownPrinter.printIssueMarkdown(issue1, SONARQUBE_URL);
    issueReport.add(issue1);

    SonarQubeIssue issue2 = new SonarQubeIssue(Severity.CRITICAL, "message2", "rule2", FILE_PATH_1, 2);
    stashCommentMessage2 = MarkdownPrinter.printIssueMarkdown(issue2, SONARQUBE_URL);
    issueReport.add(issue2);

    SonarQubeIssue issue3 = new SonarQubeIssue(Severity.INFO, "message3", "rule3", FILE_PATH_2, 1);
    stashCommentMessage3 = MarkdownPrinter.printIssueMarkdown(issue3, SONARQUBE_URL);
    issueReport.add(issue3);

    stashComments1 = mock(StashCommentReport.class);
    when(stashComments1.applyDiffReport(diffReport)).thenReturn(stashComments1);
    when(stashClient.getPullRequestComments(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, FILE_PATH_1)).thenReturn(stashComments1);

    stashComments2 = mock(StashCommentReport.class);
    when(stashComments2.applyDiffReport(diffReport)).thenReturn(stashComments2);
    when(stashClient.getPullRequestComments(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, FILE_PATH_2)).thenReturn(stashComments2);

    doReturn(COMMENT_ID).when(stashClient).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    doReturn(COMMENT_ID).when(stashClient).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    doReturn(COMMENT_ID).when(stashClient).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);

    when(config.getCommentIssueSeverityThreshold()).thenReturn(Severity.INFO);
    when(config.getTaskIssueSeverityThreshold()).thenReturn(Severity.INFO);
  }

  @Test
  public void testGetCredentials() {
    when(config.getStashLogin()).thenReturn("login");
    when(config.getStashPassword()).thenReturn("password");

    StashCredentials credentials = myFacade.getCredentials();
    assertEquals(credentials.getLogin(), "login");
    assertEquals(credentials.getPassword(), "password");
  }

  @Test
  public void testGetNoCredentials() {
    when(config.getStashLogin()).thenReturn(null);
    when(config.getStashPassword()).thenReturn(null);

    StashCredentials credentials = myFacade.getCredentials();
    assertNull(credentials.getLogin());
    assertNull(credentials.getPassword());
  }

  @Test
  public void testGetIssueThreshold() throws StashConfigurationException {
    when(config.getIssueThreshold()).thenReturn(1);
    assertEquals(myFacade.getIssueThreshold(), 1);
  }

  @Test(expected = StashConfigurationException.class)
  public void testGetIssueThresholdThrowsException() throws StashConfigurationException {
    when(config.getIssueThreshold()).thenThrow(new NumberFormatException());
    myFacade.getIssueThreshold();
  }

  @Test
  public void testGetStashURL() throws StashConfigurationException {
    when(config.getStashURL()).thenReturn("http://url");
    assertEquals(myFacade.getStashURL(), "http://url");
  }

  @Test(expected = StashConfigurationException.class)
  public void testGetStashURLThrowsException() throws StashConfigurationException {
    when(config.getStashURL()).thenReturn(null);
    myFacade.getStashURL();
  }

  @Test
  public void testGetStashProject() throws StashConfigurationException {
    when(config.getStashProject()).thenReturn("project");
    assertEquals(myFacade.getStashProject(), "project");
  }

  @Test(expected = StashConfigurationException.class)
  public void testGetStashProjectThrowsException() throws StashConfigurationException {
    when(config.getStashProject()).thenReturn(null);
    myFacade.getStashProject();
  }

  @Test
  public void testGetStashRepository() throws StashConfigurationException {
    when(config.getStashRepository()).thenReturn("repository");
    assertEquals(myFacade.getStashRepository(), "repository");
  }

  @Test(expected = StashConfigurationException.class)
  public void testGetStashRepositoryThrowsException() throws StashConfigurationException {
    when(config.getStashRepository()).thenReturn(null);
    myFacade.getStashRepository();
  }

  @Test
  public void testGetStashPullRequestId() throws StashConfigurationException {
    when(config.getPullRequestId()).thenReturn("12345");
    assertEquals(myFacade.getStashPullRequestId(), "12345");
  }

  @Test(expected = StashConfigurationException.class)
  public void testGetStashPullRequestIdThrowsException() throws StashConfigurationException {
    when(config.getPullRequestId()).thenReturn(null);
    myFacade.getStashPullRequestId();
  }

  @Test
  public void testPostCommentPerIssue() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    when(stashComments1.contains(stashCommentMessage1, FILE_PATH_1, 1)).thenReturn(true);
    when(stashComments1.contains(stashCommentMessage2, FILE_PATH_1, 2)).thenReturn(false);
    when(stashComments2.contains(stashCommentMessage3, FILE_PATH_2, 1)).thenReturn(false);

    myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, issueReport, stashClient);

    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);
  }

  @Test
  public void testIssuesBelowSeverityThreshold() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    when(config.getCommentIssueSeverityThreshold()).thenReturn(Severity.MAJOR);

    myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, issueReport, stashClient);

    verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(2)).postTaskOnComment(anyString(), eq(COMMENT_ID));
  }

  @Test
  public void testPostCommentPerIssueWithNoStashCommentAlreadyPushed() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    when(stashComments1.contains(stashCommentMessage1, FILE_PATH_1, 1)).thenReturn(true);
    when(stashComments1.contains(stashCommentMessage2, FILE_PATH_1, 2)).thenReturn(true);
    when(stashComments2.contains(stashCommentMessage3, FILE_PATH_2, 1)).thenReturn(true);

    myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, issueReport, stashClient);

    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);
  }

  @Test
  public void testPostCommentPerIssueWithNoType() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    when(stashComments1.contains(stashCommentMessage1, FILE_PATH_1, 1)).thenReturn(false);
    when(stashComments1.contains(stashCommentMessage2, FILE_PATH_1, 2)).thenReturn(false);
    when(stashComments2.contains(stashCommentMessage3, FILE_PATH_2, 1)).thenReturn(false);

    when(diffReport.getType(FILE_PATH_1, 1)).thenReturn(null);
    when(diffReport.getType(FILE_PATH_1, 2)).thenReturn(STASH_DIFF_TYPE);
    when(diffReport.getType(FILE_PATH_2, 1)).thenReturn(null);

    myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, issueReport, stashClient);

    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);
  }

  @Test
  public void testPostCommentPerIssueWithNoSonarQubeIssues() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, new SonarQubeIssuesReport(), stashClient);

    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
    verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);
  }

  @Test
  public void testPostCommentPerIssueWithExceptions() throws Exception {
    initConfigForPostCommentLineOnPullRequest();

    when(stashComments1.contains(stashCommentMessage1, FILE_PATH_1, 1)).thenReturn(false);
    when(stashComments1.contains(stashCommentMessage2, FILE_PATH_1, 2)).thenReturn(false);
    when(stashComments2.contains(stashCommentMessage3, FILE_PATH_2, 1)).thenReturn(false);

    doThrow(new StashClientException("StashClientException for Test")).when(stashClient)
      .postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);

    try {
      myFacade.postCommentPerIssue(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, SONARQUBE_URL, issueReport, stashClient);

      verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage1, FILE_PATH_1, 1, STASH_DIFF_TYPE);
      verify(stashClient, times(1)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage2, FILE_PATH_1, 2, STASH_DIFF_TYPE);
      verify(stashClient, times(0)).postCommentLineOnPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, stashCommentMessage3, FILE_PATH_2, 1, STASH_DIFF_TYPE);

    } catch (StashClientException e) {
      assertFalse("Unexpected Exception: postCommentLineOnPullRequest does not raised any StashClientException", true);
    }
  }

  @Test
  public void testApprovePullRequest() throws Exception {
    myFacade.approvePullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, "sonarqube", stashClient);
    verify(stashClient, times(1)).approvePullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID);
  }
    
  @Test
  public void addResetPullRequestApproval() throws Exception {
    myFacade.resetPullRequestApproval(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, "sonarqube", stashClient);
    verify(stashClient, times(1)).resetPullRequestApproval(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID);
  }
  
  @Test
  public void testAddPullRequestReviewer() throws Exception {
    ArrayList<StashUser> reviewers = new ArrayList<>();
    StashUser stashUser = mock(StashUser.class);
    reviewers.add(stashUser);
    
    StashPullRequest pullRequest = mock(StashPullRequest.class);
    when(pullRequest.getReviewer(STASH_USER)).thenReturn(null);
    when(pullRequest.getVersion()).thenReturn((long) 1);
    
    when(stashClient.getPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID)).thenReturn(pullRequest);
    when(stashClient.getUser(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, STASH_USER)).thenReturn(stashUser);
    
    myFacade.addPullRequestReviewer(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, STASH_USER, stashClient);
    
    verify(stashClient, times(1)).addPullRequestReviewer(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, (long) 1, reviewers);
  }
  
  @Test
  public void testAddPullRequestReviewerWithReviewerAlreadyAdded() throws Exception {
    ArrayList<StashUser> reviewers = new ArrayList<>();
    StashUser stashUser = mock(StashUser.class);
    reviewers.add(stashUser);
    
    StashPullRequest pullRequest = mock(StashPullRequest.class);
    when(pullRequest.getReviewer(STASH_USER)).thenReturn(stashUser);
    when(pullRequest.getVersion()).thenReturn((long) 1);
    
    when(stashClient.getPullRequest(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID)).thenReturn(pullRequest);
    when(stashClient.getUser(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, STASH_USER)).thenReturn(stashUser);
    
    myFacade.addPullRequestReviewer(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, STASH_USER, stashClient);
    
    verify(stashClient, times(0)).addPullRequestReviewer(STASH_PROJECT, STASH_REPOSITORY, STASH_PULLREQUEST_ID, (long) 1, reviewers);
  }
}
