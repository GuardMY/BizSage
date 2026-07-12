const industryLabels: Record<string, string> = {
  general: "通用企业", "default-industry": "通用企业", catering: "餐饮", retail: "零售", manufacturing: "制造", wholesale: "批发", ecommerce: "电商", logistics: "物流",
  "professional-services": "专业服务", education: "教育培训", healthcare: "医疗健康", construction: "建筑工程", "real-estate": "房地产", "tourism-hotel": "旅游与酒店", agriculture: "农业",
  "food-processing": "食品加工", textile: "服装纺织", automotive: "汽车及零部件", "home-building": "家居建材", beauty: "美容美发", fitness: "健身与运动", "culture-media": "文化传媒",
  "software-internet": "软件与互联网", "financial-services": "金融服务", "legal-services": "法律服务", "human-resources": "人力资源", "advertising-marketing": "广告营销", "property-management": "物业管理", housekeeping: "家政服务",
  "auto-service": "汽车维修与服务", "pharma-biotech": "医药与生物科技", "energy-environment": "能源与环保", insurance: "保险", "banking-payment": "银行与支付", "securities-investment": "证券与投资", telecom: "通信服务",
  electronics: "电子信息", semiconductor: "半导体", chemical: "化工", "new-energy": "新能源", mining: "矿业", "supply-chain": "供应链服务", "import-export": "进出口贸易", aviation: "航空",
  shipping: "航运", "express-delivery": "快递", "printing-packaging": "印刷包装", jewelry: "珠宝首饰", "maternal-child": "母婴服务", "pet-services": "宠物服务", "elderly-care": "养老服务", "funeral-services": "殡葬服务"
};

const regionLabels: Record<string, string> = {
  "default-region": "默认地区", default: "默认地区", cn: "中国", "cn-default": "中国大陆", "cn-other": "中国其他地区", us: "美国", "us-default": "美国"
};

const roleLabels: Record<string, string> = {
  USER: "用户", ADMIN: "管理员", SEED_PAID: "付费用户", INTERNAL: "内部用户", FREE: "普通用户", LEGAL_FREEZE: "已冻结"
};
const membershipLabels: Record<string, string> = { FREE: "免费版", SEED_PAID: "付费版", INTERNAL: "内部版" };

export function industryLabel(industryId: string | null | undefined, fallback?: string) {
  if (!industryId) return fallback ?? "未设置行业";
  return industryLabels[industryId] ?? fallback ?? industryId;
}

export function regionLabel(regionId: string | null | undefined) {
  if (!regionId) return "未设置地区";
  return regionLabels[regionId] ?? regionId;
}

export function roleLabel(role: string | null | undefined) {
  if (!role) return "未设置角色";
  return roleLabels[role] ?? role;
}

export function membershipLabel(membershipLevel: string | null | undefined) {
  if (!membershipLevel) return "未设置会员级别";
  return membershipLabels[membershipLevel] ?? membershipLevel;
}
