import type {
  Conversation,
  ConversationMessage,
  Diagnosis,
  DiagnosisReport,
  LoginProfile,
  RecommendationItem,
  Source
} from "../../lib/api-client";

export type { AdditionalInformationQuestion } from "../../lib/api-client";

export type WorkspaceSection = "diagnosis" | "learning" | "users" | "archive";

export type WorkspaceMessages = {
  brandSubtitle: string;
  loginTitle: string;
  loginIntro: string;
  username: string;
  password: string;
  login: string;
  loggingIn: string;
  loginNotice: string;
  loginSuccess: string;
  loginFailed: string;
  sessionExpired: string;
  languageToggle: string;
  languageSaved: string;
  navDiagnosis: string;
  navLearning: string;
  navUsers: string;
  navArchive: string;
  identity: string;
  logout: string;
  workspaceTitle: string;
  health: string;
  evidenceLine: string;
  newConversation: string;
  diagnosisConversation: string;
  learningConversation: string;
  busyDiagnosis: string;
  busyLearning: string;
  sendDiagnosis: string;
  sendLearning: string;
  diagnosisCreated: string;
  diagnosisFailed: string;
  diagnosisRetrying: string;
  conversationArchived: string;
  loginRequired: string;
  workerUnreachable: string;
  llmNotConfigured: string;
  workerTimeout: string;
  workerError: string;
  needsReview: string;
  insufficientEvidence: string;
  recommendationTitle: string;
  recommendationEmpty: string;
  recommendationRefresh: string;
  recommendationMissing: string;
  recommendationContinue: string;
  learningNextNode: string;
  learningCurrentBlock: string;
  learningExtensionDirection: string;
  diagnosisBusinessIssue: string;
  diagnosisMissingProfile: string;
  diagnosisHighImpactDetail: string;
  reportTitle: string;
  reportMetadata: string;
  reportEmptyTitle: string;
  reportEmptyDetail: string;
  source: string;
  sourceUrl: string;
  confidence: string;
  score: string;
  close: string;
  generateReport: string;
  generatingReport: string;
  selectConversation: string;
  noConversations: string;
  newConversationTitle: string;
  activeConversations: string;
  archivedConversations: string;
  diagnosisConversationList: string;
  archiveConversationList: string;
  archiveCurrent: string;
  archiveConversation: string;
  deleteConversation: string;
  archiveReadonly: string;
  selectedConversation: string;
  noArchivedMessages: string;
  usersSummary: string;
  conversationContext: string;
  noConversationContext: string;
};

export type { Conversation, ConversationMessage, Diagnosis, DiagnosisReport, LoginProfile, RecommendationItem, Source };
