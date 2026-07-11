import type {
  Conversation,
  ConversationMessage,
  Diagnosis,
  DiagnosisReport,
  LoginProfile,
  PaidIntelligence,
  RecommendationItem,
  Source
} from "../../lib/api-client";

export type WorkspaceSection = "diagnosis" | "intelligence" | "users" | "archive";

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
  navIntelligence: string;
  navUsers: string;
  navArchive: string;
  identity: string;
  logout: string;
  workspaceTitle: string;
  health: string;
  evidenceLine: string;
  newConversation: string;
  diagnosisConversation: string;
  busyDiagnosis: string;
  sendDiagnosis: string;
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
  paidTitle: string;
  paidEmptyTitle: string;
  paidEmptyDetail: string;
  recommendationTitle: string;
  recommendationEmpty: string;
  recommendationRefresh: string;
  recommendationMissing: string;
  recommendationContinue: string;
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
  intelligenceSummary: string;
  usersSummary: string;
  conversationContext: string;
  noConversationContext: string;
};

export type { Conversation, ConversationMessage, Diagnosis, DiagnosisReport, LoginProfile, PaidIntelligence, RecommendationItem, Source };
