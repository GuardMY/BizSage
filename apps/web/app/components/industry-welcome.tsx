import { ArrowRight, BriefcaseBusiness, Plus } from "lucide-react";
import type { UserIndustry } from "../../lib/api-client";

export function IndustryWelcome({ industries, selectedIndustryId, customName, onSelect,
  onCustomNameChange, onAddCustom, onStart, busy, title, detail, startLabel, customLabel }: {
  industries: UserIndustry[]; selectedIndustryId: string; customName: string;
  onSelect: (id: string) => void; onCustomNameChange: (value: string) => void;
  onAddCustom: () => void; onStart: () => void; busy: boolean; title: string;
  detail: string; startLabel: string; customLabel: string;
}) {
  return <div className="welcomeWorkspace"><section className="workspaceCard welcomeCard">
    <div className="welcomeIcon"><BriefcaseBusiness size={28} /></div>
    <h1>{title}</h1><p>{detail}</p>
    <div className="industryChoices">{industries.map((industry) => <button
      className={`industryChoice ${industry.industryId === selectedIndustryId ? "active" : ""}`}
      key={industry.industryId} onClick={() => onSelect(industry.industryId)} type="button">
      {industry.industryName}</button>)}</div>
    <div className="customIndustryRow"><input value={customName}
      onChange={(event) => onCustomNameChange(event.target.value)} placeholder={customLabel} />
      <button className="ghost" onClick={onAddCustom} disabled={!customName.trim()} type="button"><Plus size={16} /></button></div>
    <button className="primary" onClick={onStart} disabled={busy || !selectedIndustryId} type="button">
      <ArrowRight size={16} /> {startLabel}</button>
  </section></div>;
}
