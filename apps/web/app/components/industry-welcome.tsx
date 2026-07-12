import { ArrowRight, BriefcaseBusiness, Plus, Search } from "lucide-react";
import type { UserIndustry } from "../../lib/api-client";
import { useEffect, useState } from "react";

type IndustrySearchResult = { industryId: string; industryName: string; score: number };

export function IndustryWelcome({ industries, selectedIndustryId, customName, onSelect,
  onCustomNameChange, onAddCustom, onStart, onSearch, busy, title, detail, startLabel, customLabel }: {
  industries: UserIndustry[]; selectedIndustryId: string; customName: string;
  onSelect: (id: string) => void; onCustomNameChange: (value: string) => void;
  onAddCustom: () => void; onStart: () => void; busy: boolean; title: string;
  onSearch: (query: string) => Promise<IndustrySearchResult[]>; detail: string; startLabel: string; customLabel: string;
}) {
  const [query, setQuery] = useState("");
  const [visibleIndustries, setVisibleIndustries] = useState(industries);
  useEffect(() => {
    if (!query.trim()) { setVisibleIndustries(industries); return; }
    let cancelled = false;
    const timer = window.setTimeout(() => onSearch(query.trim()).then((items) => {
      if (!cancelled) setVisibleIndustries(items.map((item) => ({ ...item, userId: 0, id: 0, sourceType: "PRESET", status: "ACTIVE" })));
    }).catch(() => { if (!cancelled) setVisibleIndustries([]); }), 240);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [industries, onSearch, query]);
  return <div className="welcomeWorkspace"><section className="workspaceCard welcomeCard">
    <div className="welcomeIcon"><BriefcaseBusiness size={28} /></div>
    <h1>{title}</h1><p>{detail}</p>
    <div className="industrySearch"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} /></div>
    <div className="industryChoices">{visibleIndustries.map((industry) => <button
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
