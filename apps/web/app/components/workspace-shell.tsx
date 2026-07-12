import { Archive, BookOpen, Bot, Languages, LogOut, UserRound } from "lucide-react";
import type { ReactNode } from "react";
import type { LoginProfile, WorkspaceMessages, WorkspaceSection } from "./workspace-types";

type WorkspaceShellProps = {
  activeSection: WorkspaceSection;
  children: ReactNode;
  onLogout: () => void;
  onToggleLocale: () => void;
  profile: LoginProfile;
  setActiveSection: (section: WorkspaceSection) => void;
  sidebar: ReactNode;
  status: string;
  t: WorkspaceMessages;
};

export function WorkspaceShell({
  activeSection,
  children,
  onLogout,
  onToggleLocale,
  profile,
  setActiveSection,
  sidebar,
  status,
  t
}: WorkspaceShellProps) {
  const scrollClassName = `workspaceScroll ${activeSection === "diagnosis" ? "conversationScrollOnly" : ""}`.trim();

  return (
    <main className="workspace">
      <aside className="rail">
        <div className="brand">
          <span className="mark">BS</span>
          <div>
            <strong>BizSage</strong>
            <small>{t.brandSubtitle}</small>
          </div>
        </div>

        <nav className="nav">
          <button
            className={`navItem ${activeSection === "diagnosis" ? "active" : ""}`}
            title={t.diagnosisConversation}
            onClick={() => setActiveSection("diagnosis")}
            type="button"
          ><Bot size={18} />{t.navDiagnosis}</button>
          <button
            className={`navItem ${activeSection === "learning" ? "active" : ""}`}
            title={t.learningConversation}
            onClick={() => setActiveSection("learning")}
            type="button"
          ><BookOpen size={18} />{t.navLearning}</button>
          <button
            className={`navItem ${activeSection === "users" ? "active" : ""}`}
            title={t.navUsers}
            onClick={() => setActiveSection("users")}
            type="button"
          ><UserRound size={18} />{t.navUsers}</button>
          <button
            className={`navItem ${activeSection === "archive" ? "active" : ""}`}
            title={t.navArchive}
            onClick={() => setActiveSection("archive")}
            type="button"
          ><Archive size={18} />{t.navArchive}</button>
        </nav>

        {sidebar}
      </aside>

      <section className="workspaceBody">
        <header className="topbar">
          <div>
            <h1>{t.workspaceTitle}</h1>
            <p>{status}</p>
          </div>
          <div className="topActions">
            <section className="topbarIdentity" aria-label={t.identity}>
              <div className="topbarIdentityCopy">
                <strong>{profile.username}</strong>
                <small>{profile.role} / {profile.membershipLevel} / {profile.regionId} / {profile.industryId}</small>
              </div>
              <div className="topbarIdentityActions">
                <button className="languageButton" onClick={onToggleLocale} type="button">
                  <Languages size={16} />
                  {t.languageToggle}
                </button>
                <button className="ghost" onClick={onLogout} type="button">
                  <LogOut size={16} />
                  {t.logout}
                </button>
              </div>
            </section>
          </div>
        </header>

        <div className={scrollClassName}>{children}</div>
      </section>
    </main>
  );
}
