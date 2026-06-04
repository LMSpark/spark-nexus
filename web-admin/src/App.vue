<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, apiBlob, capabilities, login, session, switchTenant } from './api'
import heroImage from './assets/community-governance-hero.png'

type AnyRow = Record<string, any>

const ownerAppUrl = ((import.meta as unknown as { env?: Record<string, string> }).env?.VITE_OWNER_APP_URL || 'http://localhost:5684/') as string

const nav = [
  ['dashboard', 'Nexus 驾驶舱'],
  ['screen', '治理大屏'],
  ['communities', '小区档案'],
  ['billing', '账单收费'],
  ['revenue', '公共收益'],
  ['expenses', '支出审批'],
  ['votes', '投票问卷'],
  ['repairs', '报修投诉'],
  ['finance', '财务凭证'],
  ['banking', '银行端'],
  ['registrations', '注册审核'],
  ['acceptance', '验收中心'],
  ['audit', '审计日志']
] as const

type NavKey = typeof nav[number][0]
type PublicRegistrationMode = 'tenant' | 'community'

const tenantTypeOptions = [
  { label: '物业公司', value: 'PROPERTY' },
  { label: '政府部门', value: 'GOVERNMENT' },
  { label: '小区业委会', value: 'COMMITTEE' },
  { label: '银行机构', value: 'BANK' },
  { label: '社区商户', value: 'MERCHANT' }
]

const relationTypeOptions = [
  { label: '物业服务', value: 'PROPERTY_SERVICE' },
  { label: '业委会治理', value: 'COMMITTEE_GOVERN' },
  { label: '政府监管', value: 'SUPERVISION' },
  { label: '本地生活商户', value: 'LOCAL_SERVICE' }
]

const landingStats = [
  { value: '城市级', label: '以小区作为城市治理最小可运营单元，承接公共服务、居民自治与商业生态' },
  { value: '可信链', label: '身份、房屋、资金、事项、服务全链路授权留痕，让协同有边界、有证据' },
  { value: '生态座', label: '从物业管理走向社区生活入口，把本地服务沉淀为可监管的数字经济' }
]

const policyCards = [
  { title: '居委会依法有抓手', text: '公共事务、公益事业、便民服务、矛盾纠纷、物业纠纷协助，都可以沉淀为平台事项。' },
  { title: '街道社区有指挥链', text: '以社区为社会治理基本单元，把居民诉求、网格巡查、协商议事、部门协办放进闭环。' },
  { title: '业委会物业有协同面', text: '居委会牵头议事协调，业委会、物业、银行、商户按小区授权协同，不再各说各话。' },
  { title: '政府管理有数据账', text: '每个小区的诉求、调解、资金、投票、服务、风险都有台账，能督办、能考核、能复盘。' }
]

const strategicPillars = [
  { title: '从物业工具到城市社区操作系统', text: '平台不是替某一方做表格，而是把小区公共事务、居民服务和城市治理连接成一套长期运行的数字基础设施。', tag: '治理范式' },
  { title: '从信息孤岛到可信协同网络', text: '物业、业委会、政府、银行、住户、商户在同一小区主档下协作，所有数据按角色、场景、授权和审计边界流转。', tag: '协同秩序' },
  { title: '从线下窗口到社区数字经济入口', text: '缴费、报修、投票、房屋、快递、餐饮、商户服务不是流量拼盘，而是基于身份与小区授权的可信生活服务网络。', tag: '生态增长' }
]

const trustFoundations = [
  { code: '01', title: '可信身份', text: '主体注册、实名住户、房屋绑定、角色权限统一校验。' },
  { code: '02', title: '可信房屋', text: '以小区、楼栋、房屋为治理坐标，承载服务与资产关系。' },
  { code: '03', title: '可信资金', text: '账单、支付、监管账户、流水、凭证、审批同链路穿透。' },
  { code: '04', title: '可信服务', text: '商户、政务、便民和 IoT 服务按小区授权接入并可监管。' }
]

const registrationRoles = [
  { title: '小区/业委会', desc: '小区建档、业委会入驻、业主大会与公共收益治理', action: 'community', tenantType: 'COMMITTEE' },
  { title: '物业公司', desc: '收费、报修、公告、服务评价和运营数据接入', action: 'tenant', tenantType: 'PROPERTY' },
  { title: '街道/居委会', desc: '网格治理、居民议事、矛盾调解、物业协同与风险督办', action: 'tenant', tenantType: 'GOVERNMENT' },
  { title: '银行机构', desc: '代收、监管账户、对账、放款和回调验签', action: 'tenant', tenantType: 'BANK' },
  { title: '住户', desc: '账号注册、实名房屋绑定、缴费、投票和服务请求', action: 'resident', tenantType: 'OWNER' },
  { title: '社区商户', desc: '餐饮、零售、家政、维修、养老托育和团购服务入驻', action: 'tenant', tenantType: 'MERCHANT' }
]

const committeeFeatures = [
  { title: '社情民意上报', text: '居民随手拍、语音文字、位置上传，诉求自动进入居委会/网格员待办。' },
  { title: '网格巡查闭环', text: '网格员签到、巡查、事件受理、派单、处置、超时预警和考核留痕。' },
  { title: '居民议事协商', text: '议题征集、方案公示、会议记录、线上表决、结果公开和后续监督。' },
  { title: '居务公开阵地', text: '政策宣传、办事指南、居民公约、公益项目、活动报名和志愿服务发布。' },
  { title: '重点人群关爱', text: '老人、儿童、残障、困难家庭、特殊群体形成关爱台账和走访提醒。' },
  { title: '物业矛盾调解', text: '把物业、业委会、住户、银行和商户拉进同一事项，调解过程可追溯。' },
  { title: '驻区资源联动', text: '社区医院、派出所、市场监管、志愿队、骑手、商户按事项协同参与。' },
  { title: '一张图治理', text: '楼栋、房屋、网格、事件、风险、人员和公共设施在地图上统一落点。' }
]

const committeeWorkflow = [
  '居民说事',
  '网格受理',
  '居委会研判',
  '多方协办',
  '公开反馈'
]

const committeeMetrics = [
  { value: '5步', label: '民意到反馈闭环' },
  { value: '8类', label: '居委会可承接事项' },
  { value: 'N方', label: '驻区单位与社会力量' }
]

const ecosystemCapabilities = [
  { code: 'GIS', title: '空间治理能力', text: '楼栋、房屋、网格、设施、风险、事件统一落图，形成城市运行向小区延伸的空间底座。' },
  { code: '房', title: '房屋资产能力', text: '租售、空置、授权、备案、经纪服务围绕真实房屋关系流转，房源可信、交易可管。' },
  { code: '递', title: '末端配送能力', text: '快递柜、驿站、骑手、异常件、无接触配送纳入社区服务网络，解决最后一百米。' },
  { code: '餐', title: '民生供给能力', text: '助老餐、团餐、生鲜、食品安全、价格公示和居民订单进入可评价、可监管的供给体系。' },
  { code: '商', title: '本地商业能力', text: '商户资质、服务范围、优惠、订单、投诉、评价和退出机制统一沉淀，形成小区可信商圈。' },
  { code: '银', title: '金融服务能力', text: '代收、监管账户、对账、票据、保险、授信和放款围绕小区资金与主体信用闭环。' },
  { code: '政', title: '政务协同能力', text: '街道、社区、住建、市场监管、公安消防等事项按职责接入，居民诉求可分派、可督办。' },
  { code: '物', title: '物联感知能力', text: '门禁、电梯、消防、能耗、摄像头、充电桩等设备数据进入风险预警和服务调度。' }
]

const ecosystemRules = [
  { title: '统一准入', text: '主体资质、服务范围、履约能力、信用记录先审核，再允许进入小区服务场景。' },
  { title: '分级授权', text: '按小区、楼栋、房屋、账户、人员、事项和数据域授予最小必要权限。' },
  { title: '场景编排', text: '把服务能力编排进缴费、报修、议事、关爱、租售、配送等真实居民流程。' },
  { title: '过程监管', text: '订单、工单、投诉、评价、资金、票据、设备事件全程留痕，政府和居委会可看可管。' },
  { title: '价值闭环', text: '公共收益、服务评价、信用评分、运营分润和风险处置回到小区治理台账。' },
  { title: '动态退出', text: '违规商户、异常服务、风险设备和不合规接口可以降权、暂停、清退并留存证据。' }
]

const residentAppFeatures = [
  '物业缴费',
  '报修投诉',
  '业主投票',
  '公告消息',
  '房屋租售',
  '快递到件',
  '社区餐饮',
  '商户优惠',
  '停车充电',
  '门禁访客',
  '便民服务'
]

const merchantScenarios = [
  { title: '餐饮零售', text: '小区食堂、早餐店、生鲜团购、便利店和品牌折扣，按小区范围精准上架。' },
  { title: '到家服务', text: '家政保洁、维修开锁、养老托育、陪诊跑腿，从资质审核到评价都可追溯。' },
  { title: '房屋服务', text: '租售委托、看房预约、合同备案、搬家保洁，用房屋与业主授权建立可信交易。' },
  { title: '服务监管', text: '食品安全、价格公示、投诉处理、黑名单和服务评分，接受物业与政府协同监管。' }
]

const platformPrinciples = [
  '小区是城市治理的基本单元，也是居民生活服务和本地商业的真实入口',
  '平台先建立公共秩序，再承载商业生态，避免把社区服务做成无边界流量市场',
  '五方主体先注册、再授权、再协同，任何数据流转都能说清来源、用途和责任',
  '公共收益、资金账户、投票决策、支出审批、银行对账必须可穿透、可核验、可追责',
  '住户端不是另一个工具，而是把公共事务、生活服务和治理参与汇聚到一个可信入口'
]

const onboardingSteps = [
  { step: '01', title: '选择主体', text: '物业、政府、业委会、银行、住户按身份提交资料' },
  { step: '02', title: '围绕小区', text: '把主体挂接到小区，形成服务、监管、金融和住户关系' },
  { step: '03', title: '授权开通', text: '按数据域授予读取、写入、审批、导出等权限' },
  { step: '04', title: '生态接入', text: '通过 API 和运营台接 GIS、租售、快递、餐饮、商户与便民服务' }
]

const roleNav: Record<string, NavKey[]> = {
  ADMIN: ['dashboard', 'screen', 'communities', 'billing', 'revenue', 'expenses', 'votes', 'repairs', 'finance', 'banking', 'registrations', 'acceptance', 'audit'],
  GOVERNMENT: ['dashboard', 'screen', 'communities', 'revenue', 'expenses', 'finance', 'banking', 'registrations', 'acceptance', 'audit'],
  STREET: ['dashboard', 'screen', 'communities', 'revenue', 'expenses', 'finance', 'banking', 'registrations', 'acceptance'],
  COMMITTEE: ['dashboard', 'communities', 'revenue', 'expenses', 'votes', 'repairs', 'finance'],
  PROPERTY: ['dashboard', 'communities', 'billing', 'revenue', 'repairs'],
  BANK: ['banking', 'revenue'],
  MERCHANT: ['communities', 'repairs'],
  OWNER: []
}

const active = ref('dashboard')
const loading = ref(false)
const username = ref('gov')
const password = ref('admin123')
const user = ref(session.user)
const activeTenantId = ref(session.user?.tenantId || 0)
const data = ref<Record<string, any>>({})
const chartEl = ref<HTMLElement | null>(null)
const feeForm = ref({
  communityId: 1,
  feeType: '物业费',
  billingMode: 'AREA',
  unitPrice: 3,
  cycle: 'MONTHLY',
  effectiveFrom: '2026-01-01'
})
const generationForm = ref({
  communityId: 1,
  period: '2026-07',
  dueDate: '2026-07-31'
})
const arrearsPublicationForm = ref({
  communityId: 1,
  period: '2026-07',
  title: 'SPARK Nexus 欠费公开台账'
})
const expenseForm = ref({
  communityId: 1,
  orderType: 'PAYMENT',
  title: '公共区域设施维修付款',
  amount: 52000,
  invoiceNo: 'INV-2026-NEW',
  contractNo: 'HT-2026-NEW',
  invoiceIssuer: '武汉鼎盛工程服务有限公司',
  invoiceAmount: 52000,
  invoiceIssueDate: '2026-06-01',
  invoiceFileUrl: 'mock://invoice/INV-2026-NEW',
  contractParty: '武汉鼎盛工程服务有限公司',
  contractAmount: 52000,
  contractSignDate: '2026-05-28',
  contractFileUrl: 'mock://contract/HT-2026-NEW'
})
const approvalRuleForm = ref({
  communityId: 1,
  expenseThreshold: 50000,
  voteThreshold: 100000,
  approvalTimeoutHours: 24,
  voteRatio: 66.67,
  status: 'ACTIVE'
})
const voteForm = ref({
  communityId: 1,
  title: '公共收益使用满意度问卷',
  voteType: 'SURVEY',
  startAt: '2026-06-01 09:00:00',
  endAt: '2026-06-08 18:00:00',
  questionTitle: '您对本小区公共收益管理透明度是否满意？',
  optionsText: '满意\n基本满意\n不满意'
})
const announcementForm = ref({
  communityId: 1,
  category: '通知公告',
  title: '公共收益月度公示',
  content: '本月公共收益收支明细已完成核对，请业主在业主端查看。'
})
const replyDialogVisible = ref(false)
const selectedWorkOrder = ref<AnyRow | null>(null)
const workOrderReply = ref({
  status: 'DONE',
  reply: ''
})
const drilldownVisible = ref(false)
const drilldownLoading = ref(false)
const drilldown = ref<AnyRow | null>(null)
const approvalNodeVisible = ref(false)
const selectedExpense = ref<AnyRow | null>(null)
const approvalNodes = ref<AnyRow[]>([])
const workflowTemplateNodes = ref<AnyRow[]>([])
const workflowTemplateCode = ref('PUBLIC_EXPENSE')
const workflowNodeForm = ref({
  nodeCode: 'COMMITTEE_REVIEW',
  nodeName: '业委会审批',
  roleCode: 'COMMITTEE',
  sortNo: 2,
  conditionCode: 'ALWAYS',
  timeoutHours: 24,
  status: 'ACTIVE'
})
const workflowEventVisible = ref(false)
const workflowEvents = ref<AnyRow[]>([])
const expenseDocumentVisible = ref(false)
const expenseDocuments = ref<AnyRow[]>([])
const documentForm = ref({
  documentType: 'INVOICE',
  documentNo: '',
  issuer: '',
  amount: 0,
  issueDate: '2026-06-01',
  fileUrl: ''
})
const arrearsDetailVisible = ref(false)
const selectedArrearsPublication = ref<AnyRow | null>(null)
const arrearsItems = ref<AnyRow[]>([])
const surveyResultVisible = ref(false)
const selectedVote = ref<AnyRow | null>(null)
const surveyResults = ref<AnyRow[]>([])
const creditFactorVisible = ref(false)
const selectedCreditScore = ref<AnyRow | null>(null)
const creditFactors = ref<AnyRow[]>([])
const selectedVoucherIds = ref<number[]>([])
const voucherForm = ref({
  communityId: 1,
  debitSubject: '银行存款-公共收益专户',
  creditSubject: '公共收益收入',
  amount: 1000
})
const tenantRegistrationForm = ref({
  tenantType: 'PROPERTY',
  tenantName: '武汉新城物业服务有限公司',
  unifiedCreditCode: '91420100NEW000001',
  contactName: '李经理',
  contactPhone: '13800000001',
  adminUsername: 'property_admin_new',
  adminPassword: 'admin123',
  adminDisplayName: '物业机构管理员'
})
const tenantRegistrationPresets: Record<string, Partial<typeof tenantRegistrationForm.value>> = {
  PROPERTY: {
    tenantName: '武汉新城物业服务有限公司',
    unifiedCreditCode: '91420100NEW000001',
    contactName: '李经理',
    contactPhone: '13800000001',
    adminUsername: 'property_admin_new',
    adminDisplayName: '物业机构管理员'
  },
  GOVERNMENT: {
    tenantName: '新入驻社区居民委员会',
    unifiedCreditCode: '',
    contactName: '居委会经办人',
    contactPhone: '13800000002',
    adminUsername: 'committee_gov_new',
    adminDisplayName: '社区治理经办员'
  },
  COMMITTEE: {
    tenantName: '新小区业主委员会',
    unifiedCreditCode: '',
    contactName: '业委会主任',
    contactPhone: '13800000003',
    adminUsername: 'committee_new',
    adminDisplayName: '业委会经办人'
  },
  BANK: {
    tenantName: '新入驻银行支行',
    unifiedCreditCode: '91420100BANKNEW001',
    contactName: '银行客户经理',
    contactPhone: '13800000004',
    adminUsername: 'bank_new',
    adminDisplayName: '银行经办员'
  },
  MERCHANT: {
    tenantName: '新入驻社区商户',
    unifiedCreditCode: '91420100MCHNEW001',
    contactName: '商户负责人',
    contactPhone: '13800000005',
    adminUsername: 'merchant_new',
    adminDisplayName: '商户运营员'
  }
}
const publicRegistrationVisible = ref(false)
const publicRegistrationMode = ref<PublicRegistrationMode>('tenant')
const registrationCenterCommunityId = ref(1)
const communityRegistrationForm = ref({
  district: '洪山区',
  street: '关山街道',
  neighborhood: '新城社区',
  name: '新城花园',
  households: 1200,
  contactPhone: '13800000003'
})
const relationForm = ref({
  communityId: 1,
  tenantId: 4,
  relationType: 'PROPERTY_SERVICE',
  startDate: '2026-06-01'
})
const authorizationForm = ref({
  grantorTenantId: 1,
  granteeTenantId: 4,
  communityId: 1,
  dataScopes: ['COMMUNITY_PROFILE', 'HOUSE', 'BILLING'],
  permissions: ['READ']
})
const tenantUserForm = ref({
  tenantId: 4,
  username: 'property_new',
  password: 'admin123',
  displayName: '新物业管理员',
  roleCode: 'PROPERTY',
  communityId: 1
})
const bankConfigForm = ref({
  communityId: 1,
  bankTenantId: 5,
  serviceType: 'COLLECTION',
  fundAccountId: undefined as number | undefined,
  merchantNo: 'MCH-NEW-001'
})
const reconciliationForm = ref({
  communityId: 1,
  serviceType: 'COLLECTION',
  reconcileDate: '2026-06-01'
})
const paymentStatementForm = ref({
  statementDate: '2026-06-01'
})
const bankFlowImportForm = ref({
  communityId: 1,
  direction: 'IN',
  amount: 99.99,
  counterparty: '差异测试客户',
  summary: '银行导入差异流水',
  occurredAt: '2026-06-01 10:00:00',
  traceNo: 'BK-DIFF-001'
})
const relationApplicationForm = ref({
  tenantId: 4,
  communityId: 1,
  relationType: 'PROPERTY_SERVICE',
  startDate: '2026-06-01',
  dataScopes: ['COMMUNITY_PROFILE', 'HOUSE', 'BILLING', 'REPAIR', 'COMPLAINT'],
  permissions: ['READ', 'WRITE']
})
const bankServiceApplicationForm = ref({
  bankTenantId: 5,
  communityId: 1,
  serviceType: 'COLLECTION',
  merchantNo: 'MCH-APPLY-001'
})
const smsTestForm = ref({
  phone: '13800000000',
  content: 'SPARK Nexus 平台短信通道测试'
})
const identityTestForm = ref({
  name: '张女士',
  phone: '13800001024',
  identityNo: '420101199001012381',
  expectedName: '张女士',
  expectedPhoneMask: '138****1024',
  expectedIdentityMask: '4201**********2381'
})
const callbackDiagnosticForm = ref({
  provider: 'WECHAT_PAY',
  eventType: 'PAYMENT_CALLBACK',
  businessNo: 'WX-DEMO-001',
  payloadText: '{\n  "orderNo": "WX-DEMO-001",\n  "status": "SUCCESS"\n}',
  signature: ''
})
const reconciliationDetailVisible = ref(false)
const selectedReconciliation = ref<AnyRow | null>(null)

const navItems = computed(() => {
  const capabilityNav = user.value?.capabilities?.navKeys as NavKey[] | undefined
  if (capabilityNav?.length) {
    return nav.filter(([key]) => capabilityNav.includes(key))
  }
  const allowed = roleNav[user.value?.role || ''] || []
  return nav.filter(([key]) => allowed.includes(key))
})
const title = computed(() => nav.find(([key]) => key === active.value)?.[1] || '监管驾驶舱')
const roleName = computed(() => ({
  ADMIN: '系统管理员',
  GOVERNMENT: '政府监管员',
  STREET: '街道审核员',
  COMMITTEE: '业委会',
  PROPERTY: '物业人员',
  BANK: '银行人员',
  MERCHANT: '社区商户',
  OWNER: '业主'
}[user.value?.role || ''] || '未登录'))
const canWriteBilling = computed(() => hasCapability('BILLING', 'WRITE'))
const canWritePayment = computed(() => hasCapability('PAYMENT', 'WRITE'))
const canManageBankConfig = computed(() => ['ADMIN', 'GOVERNMENT', 'STREET'].includes(user.value?.role || ''))
const bankWorkbench = computed(() => data.value.bankWorkbench || {})
const bankTodos = computed(() => bankWorkbench.value.todos || [])
const bankCommunitySummaries = computed(() => bankWorkbench.value.communitySummaries || [])
const communityOptions = computed(() =>
  (data.value.communities?.length ? data.value.communities : data.value.registrationCommunityOptions) || []
)
const selectedRegistrationCommunity = computed(() =>
  communityOptions.value.find((item: AnyRow) => item.id === registrationCenterCommunityId.value)
)
const registrationCommunityRelations = computed(() => data.value.communityRelations || [])
const fivePartyCommunityStatus = computed(() => {
  const relations = registrationCommunityRelations.value
  const pendingApplications = data.value.registrations || []
  return [
    {
      party: '小区/业委会',
      owner: relations.find((item: AnyRow) => item.relationType === 'COMMITTEE_GOVERN')?.tenantName || selectedRegistrationCommunity.value?.name || '待备案',
      status: selectedRegistrationCommunity.value ? '已建档' : '待建档'
    },
    {
      party: '物业公司',
      owner: relations.find((item: AnyRow) => item.relationType === 'PROPERTY_SERVICE')?.tenantName || '待接入',
      status: relations.some((item: AnyRow) => item.relationType === 'PROPERTY_SERVICE') ? '已授权' : '待申请'
    },
    {
      party: '政府/街道',
      owner: relations.find((item: AnyRow) => ['SUPERVISION', 'JURISDICTION'].includes(item.relationType))?.tenantName || '待接入',
      status: relations.some((item: AnyRow) => ['SUPERVISION', 'JURISDICTION'].includes(item.relationType)) ? '已监管' : '待授权'
    },
    {
      party: '银行',
      owner: relations.find((item: AnyRow) => item.relationType?.startsWith('BANK_'))?.tenantName || '待开通',
      status: relations.some((item: AnyRow) => item.relationType?.startsWith('BANK_')) ? '已开通' : '待申请'
    },
    {
      party: '社区商户',
      owner: relations.find((item: AnyRow) => item.relationType === 'LOCAL_SERVICE')?.tenantName || '待入驻',
      status: relations.some((item: AnyRow) => item.relationType === 'LOCAL_SERVICE') ? '已上架' : '待授权'
    },
    {
      party: '住户',
      owner: `${pendingApplications.filter((item: AnyRow) => item.targetType === 'RESIDENT_HOUSE' && item.targetId).length} 条绑定记录`,
      status: pendingApplications.some((item: AnyRow) => item.targetType === 'RESIDENT_HOUSE' && item.status === 'PENDING') ? '有待审' : '正常'
    }
  ]
})

function hasCapability(dataScope: string, permissionCode: string) {
  if (user.value?.role === 'ADMIN') return true
  return (user.value?.capabilities?.capabilities || []).some((item: AnyRow) =>
    item.dataScope === dataScope && item.permissionCode === permissionCode && item.communityCount > 0
  )
}

async function doLogin() {
  const result = await login(username.value, password.value)
  session.set(result)
  result.capabilities = await capabilities().catch(() => undefined)
  session.set(result)
  user.value = result
  activeTenantId.value = result.tenantId
  ensureActiveModule()
  ElMessage.success(`欢迎，${result.displayName}`)
  await load()
}

async function changeTenant() {
  if (!activeTenantId.value) return
  const result = await switchTenant(activeTenantId.value)
  session.set(result)
  result.capabilities = await capabilities().catch(() => undefined)
  session.set(result)
  user.value = result
  activeTenantId.value = result.tenantId
  ensureActiveModule()
  ElMessage.success(`已切换到 ${result.tenants?.find((item) => item.tenantId === result.tenantId)?.tenantName || '当前机构'}`)
  await load()
}

function logout() {
  session.clear()
  user.value = null
  activeTenantId.value = 0
  data.value = {}
}

async function load() {
  if (!session.token) return
  ensureActiveModule()
  if (!active.value) return
  loading.value = true
  try {
    if (active.value === 'dashboard' || active.value === 'screen') {
      data.value.dashboard = await api('/api/supervision/dashboard')
      data.value.alertRules = await api('/api/supervision/alert-rules').catch(() => [])
      data.value.creditScores = await api('/api/supervision/credit-scores').catch(() => [])
      data.value.creditRules = await api('/api/supervision/credit-rules').catch(() => [])
      data.value.creditRuns = await api('/api/supervision/credit-runs').catch(() => [])
      data.value.screenTopics = await api('/api/supervision/screen/topics').catch(() => [])
    }
    if (active.value === 'communities') {
      data.value.communities = await api('/api/communities')
      data.value.houses = await api('/api/houses')
    }
    if (active.value === 'billing') {
      data.value.billing = await api('/api/billing')
      data.value.feeStandards = await api('/api/fee-standards')
      data.value.arrearsPublications = await api('/api/billing/arrears-publications')
      data.value.paymentRefunds = await api('/api/payments/refunds').catch(() => [])
      data.value.paymentStatements = await api('/api/payments/statements').catch(() => [])
      data.value.paymentReceipts = await api('/api/payments/receipts').catch(() => [])
      data.value.taxInvoices = await api('/api/tax/invoices').catch(() => [])
      data.value.communities = data.value.communities || await api('/api/communities')
    }
    if (active.value === 'revenue') data.value.revenue = await api('/api/revenue')
    if (active.value === 'expenses') {
      data.value.expenses = await api('/api/expenses')
      data.value.approvalRules = await api('/api/approval-rules')
      data.value.workflowTemplates = await api<AnyRow[]>('/api/workflow/templates').catch(() => [])
      workflowTemplateNodes.value = await api<AnyRow[]>('/api/workflow/templates/PUBLIC_EXPENSE/nodes').catch(() => [])
      data.value.communities = data.value.communities || await api('/api/communities')
    }
    if (active.value === 'votes') {
      data.value.votes = await api('/api/votes')
      data.value.communities = data.value.communities || await api('/api/communities')
    }
    if (active.value === 'repairs') {
      data.value.repairs = await api('/api/repairs')
      data.value.complaints = await api('/api/complaints')
      data.value.slaRules = await api('/api/work-orders/sla-rules')
      data.value.announcements = await api('/api/announcements')
      data.value.messages = await api('/api/messages')
      data.value.communities = data.value.communities || await api('/api/communities')
    }
    if (active.value === 'finance') {
      data.value.communities = data.value.communities || await api('/api/communities')
      data.value.accountBooks = await api('/api/finance/account-books')
      data.value.subjects = await api('/api/finance/subjects')
      data.value.initialBalances = await api('/api/finance/initial-balances')
      data.value.vouchers = await api('/api/finance/vouchers')
      data.value.ledger = await api('/api/finance/ledger')
      data.value.cashFlow = await api('/api/finance/cash-flow')
    }
    if (active.value === 'banking') {
      data.value.communities = data.value.communities || await api('/api/communities')
      data.value.registrationCommunityOptions = data.value.communities?.length ? data.value.communities : await api('/api/registrations/community-options').catch(() => [])
      data.value.tenants = data.value.tenants || await api('/api/tenants').catch(() => [])
      data.value.bankWorkbench = await api('/api/bank/workbench').catch(() => null)
      data.value.bankAdapters = await api('/api/bank/adapters').catch(() => [])
      data.value.bankConfigs = await api('/api/bank/configs')
      data.value.bankFlows = await api('/api/bank/flows')
      data.value.reconciliations = await api('/api/bank/reconciliations')
      data.value.bankDisbursements = await api('/api/bank/disbursements')
    }
    if (active.value === 'registrations') {
      data.value.registrations = await api('/api/registrations/pending')
      data.value.tenants = await api('/api/tenants')
      data.value.communities = data.value.communities || await api('/api/communities')
      data.value.registrationCommunityOptions = data.value.communities
      data.value.authorizations = await api('/api/authorizations')
      await loadTenantUsers()
      if (communityOptions.value.length) {
        registrationCenterCommunityId.value = registrationCenterCommunityId.value || communityOptions.value[0].id
        relationForm.value.communityId = registrationCenterCommunityId.value
        relationApplicationForm.value.communityId = registrationCenterCommunityId.value
        bankServiceApplicationForm.value.communityId = registrationCenterCommunityId.value
        await loadCommunityRelations()
      }
    }
    if (active.value === 'acceptance') {
      data.value.acceptance = await api('/api/acceptance/checklist')
    }
    if (active.value === 'audit') {
      data.value.audit = await api('/api/audit')
      data.value.auditVerification = await api('/api/audit/verify')
      data.value.integrations = await api('/api/integrations/status')
      data.value.gatewayStatus = await api('/api/gateway/status').catch(() => null)
      data.value.integrationConfigs = await api('/api/integrations/configs')
      data.value.integrationCalls = await api('/api/integrations/calls')
      data.value.externalCallbacks = await api('/api/integrations/callbacks').catch(() => [])
      data.value.externalCallbackNonces = await api('/api/integrations/callback-nonces').catch(() => [])
      data.value.sensitiveFieldAudits = await api('/api/integrations/sensitive-fields').catch(() => [])
      data.value.integrationSecurityChecks = await api('/api/integrations/security-checks').catch(() => [])
      data.value.productionReadiness = await api('/api/integrations/production-readiness').catch(() => null)
      data.value.deploymentReadiness = await api('/api/integrations/deployment-readiness').catch(() => null)
      data.value.databaseCompatibility = await api('/api/integrations/database-compatibility').catch(() => null)
      data.value.dataExchangePackages = await api('/api/integrations/data-exchange/packages').catch(() => [])
      data.value.exportLogs = await api('/api/audit/export-logs').catch(() => [])
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
    await nextTick()
    renderChart()
  }
}

function ensureActiveModule() {
  const first = navItems.value[0]?.[0] || ''
  if (!navItems.value.some(([key]) => key === active.value)) {
    active.value = first
  }
}

async function decide(row: AnyRow, decision: 'approve' | 'reject') {
  await api(`/api/approvals/${row.id}/${decision}`, { method: 'POST' })
  ElMessage.success(decision === 'approve' ? '已审批通过' : '已驳回并触发退回')
  await load()
}

async function openApprovalNodes(row: AnyRow) {
  selectedExpense.value = row
  approvalNodeVisible.value = true
  approvalNodes.value = await api(`/api/approvals/${row.id}/nodes`)
}

async function openWorkflowEvents(row: AnyRow) {
  selectedExpense.value = row
  workflowEventVisible.value = true
  workflowEvents.value = await api(`/api/approvals/${row.id}/events`)
}

async function downloadWorkflowEventsCsv() {
  const blob = await apiBlob('/api/workflow/events/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '工作流事件台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function editWorkflowNode(row: AnyRow) {
  workflowTemplateCode.value = row.templateCode || 'PUBLIC_EXPENSE'
  workflowNodeForm.value = {
    nodeCode: row.nodeCode,
    nodeName: row.nodeName,
    roleCode: row.roleCode,
    sortNo: Number(row.sortNo || 1),
    conditionCode: row.conditionCode || 'ALWAYS',
    timeoutHours: Number(row.timeoutHours || 24),
    status: row.status || 'ACTIVE'
  }
}

function newWorkflowNode() {
  workflowTemplateCode.value = 'PUBLIC_EXPENSE'
  workflowNodeForm.value = {
    nodeCode: `CUSTOM_${Date.now().toString().slice(-5)}`,
    nodeName: '自定义审批节点',
    roleCode: 'STREET',
    sortNo: 6,
    conditionCode: 'AMOUNT_GTE_THRESHOLD',
    timeoutHours: 24,
    status: 'ACTIVE'
  }
}

async function saveWorkflowNode() {
  await api(`/api/workflow/templates/${workflowTemplateCode.value}/nodes`, {
    method: 'POST',
    body: JSON.stringify(workflowNodeForm.value)
  })
  ElMessage.success('工作流节点已保存，事件台账已留痕')
  workflowTemplateNodes.value = await api(`/api/workflow/templates/${workflowTemplateCode.value}/nodes`)
}

async function openExpenseDocuments(row: AnyRow) {
  selectedExpense.value = row
  expenseDocumentVisible.value = true
  expenseDocuments.value = await api(`/api/expenses/${row.id}/documents`)
  documentForm.value = {
    documentType: 'INVOICE',
    documentNo: '',
    issuer: '',
    amount: Number(row.amount || 0),
    issueDate: '2026-06-01',
    fileUrl: ''
  }
}

async function openCommunityDrilldown(row: AnyRow) {
  drilldownVisible.value = true
  drilldownLoading.value = true
  try {
    drilldown.value = await api(`/api/supervision/communities/${row.id}/drilldown`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    drilldownLoading.value = false
  }
}

async function updateAlertStatus(row: AnyRow, status: 'TRACKING' | 'CLOSED') {
  await api(`/api/supervision/alerts/${row.id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status })
  })
  ElMessage.success(status === 'CLOSED' ? '预警已关闭' : '预警已进入跟踪')
  await load()
}

async function scanSupervisionAlerts() {
  const result = await api<AnyRow>('/api/supervision/alerts/scan', { method: 'POST' })
  ElMessage.success(`监管扫描完成，新增 ${result.createdCount} 条预警`)
  await load()
}

async function downloadSupervisionAlertsCsv() {
  const blob = await apiBlob('/api/supervision/alerts/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '监管预警台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function generateCreditScores() {
  const result = await api<AnyRow>('/api/supervision/credit-scores/generate', { method: 'POST' })
  ElMessage.success(`已按 ${result.ruleVersion || '当前规则'} 生成 ${result.generatedCount} 个小区信用评分`)
  await load()
}

async function downloadCreditRulesCsv() {
  const blob = await apiBlob('/api/supervision/credit-rules/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '信用评分规则.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadCreditScoresCsv() {
  const blob = await apiBlob('/api/supervision/credit-scores/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '小区信用评分.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function openCreditFactors(row: AnyRow) {
  selectedCreditScore.value = row
  creditFactors.value = await api(`/api/supervision/credit-scores/${row.id}/factors`)
  creditFactorVisible.value = true
}

async function downloadCreditFactorsCsv() {
  if (!selectedCreditScore.value) return
  const blob = await apiBlob(`/api/supervision/credit-scores/${selectedCreditScore.value.id}/factors/export`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${selectedCreditScore.value.communityName || '小区'}信用评分因子.csv`
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadScreenTopicsCsv() {
  const blob = await apiBlob('/api/supervision/screen/topics/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '监管大屏专题分析.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function prepay(row: AnyRow, channel: 'wechat' | 'alipay' = 'wechat') {
  const result = await api<AnyRow>(`/api/payments/${channel}/prepay`, {
    method: 'POST',
    body: JSON.stringify({ billId: row.id })
  })
  await api(`/api/payments/${channel}/confirm`, {
    method: 'POST',
    body: JSON.stringify({ billId: row.id, orderNo: result.orderNo, prepayId: result.prepayId, tradeNo: result.tradeNo })
  })
  ElMessage.success(`${channel === 'alipay' ? '支付宝' : '微信'}支付完成，流水已同步`)
  await load()
}

async function refundBill(row: AnyRow, channel: 'wechat' | 'alipay') {
  const result = await api<AnyRow>(`/api/payments/${channel}/refund`, {
    method: 'POST',
    body: JSON.stringify({ billId: row.id, reason: `${row.billType || '账单'}退款` })
  })
  ElMessage.success(`${channel === 'alipay' ? '支付宝' : '微信'}退款已处理：¥${money(result.amount)}`)
  await load()
}

async function downloadBillingCsv() {
  const blob = await apiBlob('/api/billing/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '账单明细台账.csv'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('账单明细已导出')
}

async function syncPaymentStatement(channel: 'wechat' | 'alipay') {
  const result = await api<AnyRow>(`/api/payments/${channel}/statement-sync`, {
    method: 'POST',
    body: JSON.stringify(paymentStatementForm.value)
  })
  ElMessage.success(`${channel === 'alipay' ? '支付宝' : '微信'}账单同步完成：${result.syncedCount} 笔`)
  await load()
}

async function issueBillReceipt(row: AnyRow) {
  const result = await api<AnyRow>(`/api/billing/${row.id}/receipt`, { method: 'POST' })
  ElMessage.success(`电子票据已开具：${result.receiptNo}`)
  await load()
}

async function downloadPaymentReceiptsCsv() {
  const blob = await apiBlob('/api/payments/receipts/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '电子缴费票据.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadPaymentReceiptPdf(row: AnyRow) {
  const blob = await apiBlob(`/api/payments/receipts/${row.id}/pdf`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.receiptNo || '电子票据'}.pdf`
  link.click()
  URL.revokeObjectURL(url)
}

async function issueTaxInvoice(row: AnyRow) {
  const result = await api<AnyRow>(`/api/payments/receipts/${row.id}/tax-invoice`, {
    method: 'POST',
    body: JSON.stringify({
      buyerName: row.payerName,
      buyerTaxNo: 'PERSONAL',
      invoiceItem: row.billType,
      taxCategoryCode: '304080299',
      taxRate: 0.06
    })
  })
  ElMessage.success(`税务开票已提交：${result.taxInvoiceNo}`)
  await load()
}

async function redCancelTaxInvoice(row: AnyRow) {
  const result = await api<AnyRow>(`/api/tax/invoices/${row.id}/red-cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason: '物业缴费发票红冲' })
  })
  ElMessage.success(`红冲已受理：${result.redInvoiceNo}`)
  await load()
}

async function downloadTaxInvoicesCsv() {
  const blob = await apiBlob('/api/tax/invoices/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '税务发票台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadTaxInvoicePdf(row: AnyRow) {
  const blob = await apiBlob(`/api/tax/invoices/${row.id}/pdf`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.invoiceRequestNo || '数电票'}.pdf`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('数电票 PDF 已下载')
}

async function createFeeStandard() {
  await api('/api/fee-standards', {
    method: 'POST',
    body: JSON.stringify(feeForm.value)
  })
  ElMessage.success('收费标准已保存')
  await load()
}

async function generateBills() {
  const result = await api<AnyRow>('/api/billing/generate', {
    method: 'POST',
    body: JSON.stringify(generationForm.value)
  })
  ElMessage.success(`已生成 ${result.generatedCount} 张账单，合计 ¥${money(result.totalAmount)}`)
  await load()
}

async function createArrearsPublication() {
  const result = await api<AnyRow>('/api/billing/arrears-publications', {
    method: 'POST',
    body: JSON.stringify(arrearsPublicationForm.value)
  })
  ElMessage.success(`欠费公示已生成：${result.arrearsCount} 笔，合计 ¥${money(result.arrearsAmount)}`)
  await load()
}

async function openArrearsItems(row: AnyRow) {
  selectedArrearsPublication.value = row
  arrearsItems.value = await api(`/api/billing/arrears-publications/${row.id}/items`)
  arrearsDetailVisible.value = true
}

async function downloadArrearsCsv(row: AnyRow) {
  const blob = await apiBlob(`/api/billing/arrears-publications/${row.id}/export`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.title || '欠费公开台账'}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

async function createExpense() {
  const result = await api<AnyRow>('/api/expenses', {
    method: 'POST',
    body: JSON.stringify(expenseForm.value)
  })
  ElMessage.success(`支出单已提交：${result.currentNode}`)
  await load()
}

async function addExpenseDocument() {
  if (!selectedExpense.value) return
  const result = await api<AnyRow>(`/api/expenses/${selectedExpense.value.id}/documents`, {
    method: 'POST',
    body: JSON.stringify(documentForm.value)
  })
  ElMessage.success(`资料已加入台账：#${result.documentId}`)
  await openExpenseDocuments(selectedExpense.value)
}

async function verifyExpenseDocument(row: AnyRow, decision: 'PASS' | 'REJECT') {
  if (!selectedExpense.value) return
  const result = await api<AnyRow>(`/api/expenses/${selectedExpense.value.id}/documents/${row.id}/verify`, {
    method: 'POST',
    body: JSON.stringify({ decision, comment: decision === 'PASS' ? '资料核验通过' : '资料需补正' })
  })
  ElMessage.success(`资料核验状态：${result.status}`)
  await openExpenseDocuments(selectedExpense.value)
}

async function downloadExpenseDocument(row: AnyRow) {
  if (!selectedExpense.value) return
  const blob = await apiBlob(`/api/expenses/${selectedExpense.value.id}/documents/${row.id}/download`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `支出资料-${row.documentNo || row.id}.pdf`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('资料附件已下载')
}

async function saveApprovalRule() {
  const result = await api<AnyRow>('/api/approval-rules', {
    method: 'POST',
    body: JSON.stringify(approvalRuleForm.value)
  })
  ElMessage.success(`审批规则已保存：#${result.approvalRuleId}`)
  await load()
}

async function scanTimeoutExpenses() {
  const result = await api<AnyRow>('/api/expenses/timeout-scan', { method: 'POST' })
  ElMessage.success(`已扫描，退回 ${result.returnedCount} 笔超时支出`)
  await load()
}

async function createVote() {
  const result = await api<AnyRow>('/api/votes', {
    method: 'POST',
    body: JSON.stringify({
      ...voteForm.value,
      options: voteForm.value.optionsText.split('\n').map((item) => item.trim()).filter(Boolean)
    })
  })
  ElMessage.success(`投票/问卷已发布：#${result.voteId}`)
  await load()
}

async function openSurveyResults(row: AnyRow) {
  selectedVote.value = row
  surveyResults.value = await api(`/api/votes/${row.id}/survey-results`)
  surveyResultVisible.value = true
}

async function createAnnouncement() {
  const result = await api<AnyRow>('/api/announcements', {
    method: 'POST',
    body: JSON.stringify(announcementForm.value)
  })
  ElMessage.success(`公告已发布：#${result.announcementId}`)
  await load()
}

function openReplyDialog(row: AnyRow) {
  selectedWorkOrder.value = row
  workOrderReply.value = {
    status: row.status === 'DONE' ? 'DONE' : 'PROCESSING',
    reply: row.reply || ''
  }
  replyDialogVisible.value = true
}

async function submitWorkOrderReply() {
  if (!selectedWorkOrder.value) return
  await api(`/api/work-orders/${selectedWorkOrder.value.id}/reply`, {
    method: 'POST',
    body: JSON.stringify(workOrderReply.value)
  })
  ElMessage.success('工单处理结果已同步给业主')
  replyDialogVisible.value = false
  await load()
}

async function scanWorkOrderSla() {
  const result = await api<AnyRow>('/api/work-orders/sla-scan', { method: 'POST' })
  ElMessage.success(`SLA 扫描完成，标记超时 ${result.overdueCount} 个工单`)
  await load()
}

async function downloadVoucherPdf(row: AnyRow) {
  const blob = await apiBlob(`/api/finance/vouchers/${row.id}/pdf`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${row.voucherNo}.pdf`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('凭证 PDF 已生成')
}

async function downloadFinanceCsv(kind: 'ledger' | 'cash-flow') {
  const blob = await apiBlob(`/api/finance/${kind}/export`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = kind === 'ledger' ? '总分类账.csv' : '现金流量表.csv'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(kind === 'ledger' ? '总分类账已导出' : '现金流量表已导出')
}

async function downloadFinancePdf(kind: 'ledger' | 'cash-flow') {
  const blob = await apiBlob(`/api/finance/${kind}/pdf`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = kind === 'ledger' ? '总分类账.pdf' : '现金流量表.pdf'
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(kind === 'ledger' ? '总分类账 PDF 已生成' : '现金流量表 PDF 已生成')
}

async function createManualVoucher() {
  const result = await api<AnyRow>('/api/finance/vouchers', {
    method: 'POST',
    body: JSON.stringify(voucherForm.value)
  })
  ElMessage.success(`手动凭证已进入审核：${result.voucherNo}`)
  await load()
}

async function reviewVoucher(row: AnyRow, decision: 'approve' | 'reject') {
  const result = await api<AnyRow>(`/api/finance/vouchers/${row.id}/review`, {
    method: 'POST',
    body: JSON.stringify({ decision })
  })
  ElMessage.success(result.status === 'APPROVED' ? '凭证已审核通过' : '凭证已驳回')
  await load()
}

function handleVoucherSelection(rows: AnyRow[]) {
  selectedVoucherIds.value = rows.map(row => Number(row.id))
}

async function batchReviewVouchers(decision: 'approve' | 'reject') {
  if (!selectedVoucherIds.value.length) {
    ElMessage.warning('请先选择凭证')
    return
  }
  const result = await api<AnyRow>('/api/finance/vouchers/batch-review', {
    method: 'POST',
    body: JSON.stringify({ voucherIds: selectedVoucherIds.value, decision })
  })
  ElMessage.success(`已批量审核 ${result.updatedCount} 张凭证`)
  selectedVoucherIds.value = []
  await load()
}

async function batchBookVouchers() {
  if (!selectedVoucherIds.value.length) {
    ElMessage.warning('请先选择凭证')
    return
  }
  const result = await api<AnyRow>('/api/finance/vouchers/batch-book', {
    method: 'POST',
    body: JSON.stringify({ voucherIds: selectedVoucherIds.value })
  })
  ElMessage.success(`已批量记账 ${result.bookedCount} 张凭证`)
  selectedVoucherIds.value = []
  await load()
}

async function submitTenantRegistration() {
  const result = await api<AnyRow>('/api/registrations/tenants', {
    method: 'POST',
    body: JSON.stringify(tenantRegistrationForm.value)
  })
  ElMessage.success(`注册申请已提交：${result.applicationNo}`)
  publicRegistrationVisible.value = false
  if (session.token) await load()
}

async function submitCommunityRegistration() {
  const result = await api<AnyRow>('/api/registrations/communities', {
    method: 'POST',
    body: JSON.stringify(communityRegistrationForm.value)
  })
  ElMessage.success(`小区备案申请已提交：${result.applicationNo}`)
  publicRegistrationVisible.value = false
  if (session.token) await load()
}

function openPublicRegistration(mode: PublicRegistrationMode, tenantType = 'PROPERTY') {
  publicRegistrationMode.value = mode
  if (mode === 'tenant') {
    Object.assign(tenantRegistrationForm.value, {
      tenantType,
      adminPassword: 'admin123'
    }, tenantRegistrationPresets[tenantType] || tenantRegistrationPresets.PROPERTY)
  }
  publicRegistrationVisible.value = true
}

function openResidentRegistrationHint() {
  window.open(ownerAppUrl, '_blank')
  ElMessage.success('已打开住户移动端，请在业主端注册并提交实名房屋绑定审核')
}

function scrollToLanding(sectionId: string) {
  document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function changeRegistrationCommunity() {
  relationForm.value.communityId = registrationCenterCommunityId.value
  relationApplicationForm.value.communityId = registrationCenterCommunityId.value
  bankServiceApplicationForm.value.communityId = registrationCenterCommunityId.value
  await loadCommunityRelations()
}

async function reviewRegistration(row: AnyRow, decision: 'approve' | 'reject') {
  await api(`/api/registrations/${row.id}/${decision}`, {
    method: 'POST',
    body: JSON.stringify({ comment: decision === 'approve' ? '资料完整，同意准入' : '资料不完整，退回补充' })
  })
  ElMessage.success(decision === 'approve' ? '注册申请已通过' : '注册申请已驳回')
  await load()
}

async function loadCommunityRelations() {
  if (!relationForm.value.communityId) return
  data.value.communityRelations = await api(`/api/communities/${relationForm.value.communityId}/relations`)
}

async function createCommunityRelation() {
  await api(`/api/communities/${relationForm.value.communityId}/relations`, {
    method: 'POST',
    body: JSON.stringify({
      tenantId: relationForm.value.tenantId,
      relationType: relationForm.value.relationType,
      startDate: relationForm.value.startDate
    })
  })
  ElMessage.success('小区机构关系已绑定')
  await loadCommunityRelations()
}

async function disableCommunityRelation(row: AnyRow) {
  await api(`/api/communities/${row.communityId}/relations/${row.id}/disable`, { method: 'POST' })
  ElMessage.success('小区机构关系已停用')
  await loadCommunityRelations()
}

async function createAuthorization() {
  await api('/api/authorizations', {
    method: 'POST',
    body: JSON.stringify(authorizationForm.value)
  })
  ElMessage.success('数据授权已创建')
  data.value.authorizations = await api('/api/authorizations')
}

async function revokeAuthorization(row: AnyRow) {
  await api(`/api/authorizations/${row.id}/revoke`, { method: 'POST' })
  ElMessage.success('授权已撤销')
  data.value.authorizations = await api('/api/authorizations')
}

async function downloadRegistrationsCsv() {
  const blob = await apiBlob('/api/registrations/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '注册审核台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadAuthorizationsCsv() {
  const blob = await apiBlob('/api/authorizations/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '数据授权台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function loadTenantUsers() {
  if (!tenantUserForm.value.tenantId) return
  data.value.tenantUsers = await api(`/api/tenants/${tenantUserForm.value.tenantId}/users`)
}

async function createTenantUser() {
  await api(`/api/tenants/${tenantUserForm.value.tenantId}/users`, {
    method: 'POST',
    body: JSON.stringify(tenantUserForm.value)
  })
  ElMessage.success('机构用户已创建/绑定')
  await loadTenantUsers()
}

async function disableTenantUser(row: AnyRow) {
  await api(`/api/tenants/${row.tenantId}/users/${row.userId}/disable`, { method: 'POST' })
  ElMessage.success('机构用户已停用')
  await loadTenantUsers()
}

async function refreshBankWorkbench() {
  if (active.value !== 'banking') return
  data.value.bankWorkbench = await api('/api/bank/workbench').catch(() => null)
}

async function createBankConfig() {
  await api('/api/bank/configs', {
    method: 'POST',
    body: JSON.stringify(bankConfigForm.value)
  })
  ElMessage.success('银行服务配置已保存')
  data.value.bankConfigs = await api('/api/bank/configs')
  await refreshBankWorkbench()
}

async function runReconciliation() {
  const result = await api<AnyRow>('/api/bank/reconciliations/run', {
    method: 'POST',
    body: JSON.stringify(reconciliationForm.value)
  })
  ElMessage.success(result.status === 'MATCHED' ? '对账平衡' : `对账差异 ¥${money(result.diffAmount)}`)
  data.value.reconciliations = await api('/api/bank/reconciliations')
  await refreshBankWorkbench()
}

async function importBankFlow() {
  await api('/api/bank/flows/import', {
    method: 'POST',
    body: JSON.stringify(bankFlowImportForm.value)
  })
  ElMessage.success('银行流水已导入')
  if (active.value === 'banking') {
    data.value.bankFlows = await api('/api/bank/flows')
    data.value.reconciliations = await api('/api/bank/reconciliations')
  }
  await refreshBankWorkbench()
}

async function downloadBankFlowsCsv() {
  const blob = await apiBlob('/api/bank/flows/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '银行流水台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function runBankAdapterHealthCheck() {
  const result = await api<AnyRow>('/api/bank/adapters/health-check', { method: 'POST' })
  ElMessage.success(`银行适配器检查完成：${result.passed}/${result.checked} 通过`)
  data.value.bankAdapters = await api('/api/bank/adapters')
  await refreshBankWorkbench()
}

async function downloadBankAdapterHealthCsv() {
  const blob = await apiBlob('/api/bank/adapters/health/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '银行适配器健康检查.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function openReconciliationDetails(row: AnyRow) {
  selectedReconciliation.value = row
  data.value.reconciliationDetails = await api(`/api/bank/reconciliations/${row.id}/details`)
  reconciliationDetailVisible.value = true
}

async function resolveReconciliationDetail(row: AnyRow, handledStatus: 'RESOLVED' | 'IGNORED') {
  if (!selectedReconciliation.value) return
  await api(`/api/bank/reconciliations/${selectedReconciliation.value.id}/details/${row.id}/resolve`, {
    method: 'POST',
    body: JSON.stringify({
      handledStatus,
      remark: handledStatus === 'RESOLVED' ? '管理端确认差异已处理' : '管理端确认差异无需处理'
    })
  })
  ElMessage.success(handledStatus === 'RESOLVED' ? '差异已处理' : '差异已忽略')
  data.value.reconciliationDetails = await api(`/api/bank/reconciliations/${selectedReconciliation.value.id}/details`)
  data.value.reconciliations = await api('/api/bank/reconciliations')
  await refreshBankWorkbench()
}

async function downloadReconciliationDetailsCsv() {
  if (!selectedReconciliation.value) return
  const id = selectedReconciliation.value.id
  const blob = await apiBlob(`/api/bank/reconciliations/${id}/details/export`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${selectedReconciliation.value.communityName || '银行对账'}-${selectedReconciliation.value.reconcileDate || id}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

async function processDisbursement(row: AnyRow, decision: 'PAY' | 'RETURN') {
  const result = await api<AnyRow>(`/api/bank/disbursements/${row.id}/process`, {
    method: 'POST',
    body: JSON.stringify({
      decision,
      remark: decision === 'PAY' ? '管理端确认银行直连放款' : '管理端退回放款指令'
    })
  })
  ElMessage.success(decision === 'PAY' ? `放款成功：${result.bankTraceNo}` : '放款指令已退回')
  await load()
  await refreshBankWorkbench()
}

async function submitRelationApplication() {
  if (relationApplicationForm.value.relationType === 'SUPERVISION') {
    relationApplicationForm.value.dataScopes = ['COMMUNITY_PROFILE', 'HOUSE', 'BILLING', 'BANK_FLOW', 'PUBLIC_REVENUE', 'EXPENSE', 'COMPLAINT', 'REPAIR', 'VOTE']
    relationApplicationForm.value.permissions = ['READ', 'APPROVE']
  } else if (relationApplicationForm.value.relationType === 'COMMITTEE_GOVERN') {
    relationApplicationForm.value.dataScopes = ['COMMUNITY_PROFILE', 'PUBLIC_REVENUE', 'EXPENSE', 'VOTE', 'REPAIR', 'COMPLAINT', 'BANK_FLOW']
    relationApplicationForm.value.permissions = ['READ', 'APPROVE']
  } else if (relationApplicationForm.value.relationType === 'LOCAL_SERVICE') {
    relationApplicationForm.value.dataScopes = ['COMMUNITY_PROFILE', 'HOUSE', 'RESIDENT', 'REPAIR', 'COMPLAINT']
    relationApplicationForm.value.permissions = ['READ', 'WRITE']
  } else {
    relationApplicationForm.value.dataScopes = ['COMMUNITY_PROFILE', 'HOUSE', 'BILLING', 'REPAIR', 'COMPLAINT']
    relationApplicationForm.value.permissions = ['READ', 'WRITE']
  }
  const result = await api<AnyRow>('/api/registrations/community-relations', {
    method: 'POST',
    body: JSON.stringify(relationApplicationForm.value)
  })
  ElMessage.success(`小区服务申请已提交：${result.applicationNo}`)
  await load()
  if (active.value === 'registrations') await loadCommunityRelations()
}

async function submitBankServiceApplication() {
  if (user.value?.role === 'BANK' && user.value.tenantId) {
    bankServiceApplicationForm.value.bankTenantId = user.value.tenantId
  }
  const result = await api<AnyRow>('/api/registrations/bank-services', {
    method: 'POST',
    body: JSON.stringify(bankServiceApplicationForm.value)
  })
  ElMessage.success(`银行服务申请已提交：${result.applicationNo}`)
  await load()
  if (active.value === 'registrations') await loadCommunityRelations()
}

async function sendTestSms() {
  const result = await api<AnyRow>('/api/integrations/sms/test', {
    method: 'POST',
    body: JSON.stringify(smsTestForm.value)
  })
  ElMessage.success(`短信通道返回：${result.result}`)
  data.value.integrationCalls = await api('/api/integrations/calls')
}

async function runIdentityTest() {
  const result = await api<AnyRow>('/api/integrations/identity/test', {
    method: 'POST',
    body: JSON.stringify(identityTestForm.value)
  })
  data.value.identityTestResult = result
  data.value.integrationCalls = await api('/api/integrations/calls')
  ElMessage[result.verified ? 'success' : 'warning'](result.verified ? '实名认证核验通过' : `实名认证未通过：${result.result}`)
}

async function runCallbackSignatureDiagnostics() {
  let payload: AnyRow
  try {
    payload = JSON.parse(callbackDiagnosticForm.value.payloadText || '{}')
  } catch {
    ElMessage.error('回调载荷不是合法 JSON')
    return
  }
  const result = await api<AnyRow>('/api/integrations/callbacks/signature-diagnostics', {
    method: 'POST',
    body: JSON.stringify({
      provider: callbackDiagnosticForm.value.provider,
      eventType: callbackDiagnosticForm.value.eventType,
      businessNo: callbackDiagnosticForm.value.businessNo,
      payload,
      signature: callbackDiagnosticForm.value.signature
    })
  })
  data.value.callbackDiagnosticResult = result
  data.value.integrationCalls = await api('/api/integrations/calls')
  ElMessage[result.verified ? 'success' : 'warning'](result.verified ? '回调签名验证通过' : `回调签名未通过：${result.signatureStatus}`)
}

async function runIntegrationSecurityChecks() {
  const result = await api<AnyRow>('/api/integrations/security-checks/run', { method: 'POST' })
  ElMessage[result.failed > 0 ? 'warning' : 'success'](`外部接口安全体检完成：通过 ${result.passed}，预警 ${result.warnings}，失败 ${result.failed}`)
  data.value.integrationSecurityChecks = await api('/api/integrations/security-checks')
  data.value.productionReadiness = await api('/api/integrations/production-readiness').catch(() => null)
  data.value.integrationCalls = await api('/api/integrations/calls')
}

async function syncIntegrationConfigs() {
  const result = await api<AnyRow>('/api/integrations/configs/sync', { method: 'POST' })
  ElMessage.success(`适配器配置已同步：${result.syncedCount} 项`)
  data.value.integrationConfigs = await api('/api/integrations/configs')
  data.value.productionReadiness = await api('/api/integrations/production-readiness').catch(() => null)
  data.value.integrationCalls = await api('/api/integrations/calls')
}

async function downloadIntegrationSecurityCsv() {
  const blob = await apiBlob('/api/integrations/security-checks/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '外部接口安全体检.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadProductionReadinessCsv() {
  const blob = await apiBlob('/api/integrations/production-readiness/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '外部接口生产上线门禁.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadDeploymentReadinessCsv() {
  const blob = await apiBlob('/api/integrations/deployment-readiness/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '部署运行就绪检查.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadDatabaseCompatibilityCsv() {
  const blob = await apiBlob('/api/integrations/database-compatibility/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '国产数据库兼容矩阵.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function createDataExchangePackage() {
  const result = await api<AnyRow>('/api/integrations/data-exchange/packages', {
    method: 'POST',
    body: JSON.stringify({
      targetParty: 'GOVERNMENT',
      dataDomains: ['BILLING', 'BANK_FLOW', 'PUBLIC_REVENUE', 'RISK_ALERT', 'CREDIT_SCORE', 'EXPORT_LOG']
    })
  })
  ElMessage.success(`数据交换包已生成：${result.packageNo}，${result.recordCount} 条`)
  data.value.dataExchangePackages = await api('/api/integrations/data-exchange/packages')
  data.value.integrationCalls = await api('/api/integrations/calls')
}

async function downloadDataExchangePackagesCsv() {
  const blob = await apiBlob('/api/integrations/data-exchange/packages/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '数据交换包台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadExternalCallbacksCsv() {
  const blob = await apiBlob('/api/integrations/callbacks/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '外部回调回执台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadExternalCallbackNoncesCsv() {
  const blob = await apiBlob('/api/integrations/callback-nonces/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '外部回调防重放台账.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadSensitiveFieldAuditsCsv() {
  const blob = await apiBlob('/api/integrations/sensitive-fields/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '敏感字段保护审计.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function verifyAuditChain() {
  data.value.auditVerification = await api('/api/audit/verify')
  const status = data.value.auditVerification.status
  ElMessage[status === 'VALID' ? 'success' : 'error'](status === 'VALID' ? '审计哈希链完整' : '审计哈希链存在断点')
  data.value.audit = await api('/api/audit')
}

async function downloadAuditCsv() {
  const blob = await apiBlob('/api/audit/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '审计日志.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function downloadAcceptanceCsv() {
  const blob = await apiBlob('/api/acceptance/checklist/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '功能落地验收清单.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function runAcceptanceDrill() {
  const result = await api<AnyRow>('/api/acceptance/drill/run', { method: 'POST' })
  ElMessage.success(`验收演练完成：支付#${result.paymentOrderId}，对账#${result.reconciliationId}`)
  data.value.acceptance = await api('/api/acceptance/checklist')
}

async function bookVoucher(row: AnyRow) {
  await api(`/api/finance/vouchers/${row.id}/book`, { method: 'POST' })
  ElMessage.success('凭证已记账')
  await load()
}

function money(value: number | string) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusType(status: string) {
  if (['PAID', 'APPROVED', 'BOOKED', 'DONE', 'CLOSED', 'VERIFIED', 'REFUNDED', 'MATCHED', 'RESOLVED', 'AUTO_CLOSED', 'PASS', 'READY'].includes(status)) return 'success'
  if (['PENDING', 'OPEN', 'PROCESSING', 'REVIEWING', 'TRACKING', 'WARN'].includes(status)) return 'warning'
  if (['OVERDUE', 'WARNING', 'REJECTED', 'FAIL', 'BLOCKED'].includes(status)) return 'danger'
  return 'info'
}

function renderChart() {
  if (!['dashboard', 'screen'].includes(active.value) || !chartEl.value || !data.value.dashboard?.trend) return
  const chart = echarts.init(chartEl.value)
  const trend = data.value.dashboard.trend
  chart.setOption({
    grid: { top: 24, right: 24, bottom: 28, left: 52 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['收入', '支出'], right: 12 },
    xAxis: { type: 'category', data: trend.map((item: AnyRow) => item.month), axisLine: { lineStyle: { color: '#d9e2e7' } } },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}' }, splitLine: { lineStyle: { color: '#edf2f5' } } },
    series: [
      { name: '收入', type: 'line', smooth: true, areaStyle: { opacity: 0.08 }, data: trend.map((item: AnyRow) => item.income), color: '#158f84' },
      { name: '支出', type: 'bar', data: trend.map((item: AnyRow) => item.expense), color: '#d98d21', barWidth: 18 }
    ]
  })
}

watch(active, load)
onMounted(async () => {
  ensureActiveModule()
  await load()
})
</script>

<template>
  <div v-if="!user" class="landing-page">
    <header class="landing-nav">
      <div class="landing-brand">
        <span class="brand-mark">SN</span>
        <div>
          <strong>SPARK Nexus</strong>
          <small>城市物业治理中枢</small>
        </div>
      </div>
      <nav>
        <button @click="scrollToLanding('policy')">国家政策</button>
        <button @click="scrollToLanding('committee')">居委会抓手</button>
        <button @click="scrollToLanding('registration')">入驻中心</button>
        <button @click="scrollToLanding('resident-app')">住户端</button>
        <button @click="scrollToLanding('merchant')">商户入驻</button>
        <button @click="scrollToLanding('ecosystem')">生态接入</button>
      </nav>
      <el-button type="primary" @click="scrollToLanding('login')">登录管理端</el-button>
    </header>

    <section class="landing-hero">
      <div class="hero-media" :style="{ backgroundImage: `url(${heroImage})` }"></div>
      <div class="hero-content">
        <h1>小区为中心的五方共治与社区生态平台</h1>
        <p>把物业公司、政府街道、业主委员会、银行与住户统一接入一个数字底座；再让社区商户、GIS、可信房屋、快递、餐饮、便民服务、IoT 安防按小区授权接入。</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="scrollToLanding('registration')">立即入驻</el-button>
          <el-button size="large" plain @click="openPublicRegistration('tenant', 'MERCHANT')">商户入驻</el-button>
          <el-button size="large" plain @click="scrollToLanding('login')">登录管理端</el-button>
        </div>
        <div class="landing-stats">
          <article v-for="item in landingStats" :key="item.value">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>
      </div>
      <section id="login" class="login-panel landing-login-panel">
        <div>
          <h2>登录管理端</h2>
          <p>政府监管、物业运营、业委会治理、银行对账统一工作台</p>
        </div>
        <el-form label-position="top" @submit.prevent="doLogin">
          <el-form-item label="账号">
            <el-input v-model="username" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" size="large" show-password />
          </el-form-item>
          <el-button type="primary" size="large" class="login-button" @click="doLogin">登录系统</el-button>
          <div class="demo-users">演示账号：admin / gov / street / committee / property / bank，密码均为 admin123。</div>
        </el-form>
      </section>
    </section>

    <main class="landing-main">
      <section id="policy" class="landing-section">
        <div class="landing-section-head">
          <h2>把政策落到居委会，把政府管理落到小区</h2>
          <p>居委会是政府联系居民、组织自治、协调物业矛盾和动员社会力量的基层抓手。SPARK Nexus 把这个抓手数字化，让街道社区不是旁观者，而是小区治理的牵引者。</p>
        </div>
        <div class="policy-grid">
          <article v-for="item in policyCards" :key="item.title">
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <section id="committee" class="landing-section committee-section">
        <div class="committee-brief">
          <h2>居委会工作台：让政府管理有入口、有台账、有闭环</h2>
          <p>把“居民找谁说、社区怎么办、物业谁来协调、部门如何协同、结果如何公开”做成标准流程。居委会不是多一个账号，而是整个平台的基层治理中枢。</p>
          <div class="committee-flow">
            <span v-for="item in committeeWorkflow" :key="item">{{ item }}</span>
          </div>
          <div class="committee-metrics">
            <article v-for="item in committeeMetrics" :key="item.label">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </article>
          </div>
        </div>
        <div class="committee-feature-grid">
          <article v-for="item in committeeFeatures" :key="item.title">
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <section id="registration" class="landing-section registration-section">
        <div class="landing-section-head">
          <h2>居委会牵引五方治理主体入驻，商户生态再上架</h2>
          <p>先建小区主档，再让街道社区、居委会、物业、业委会、银行、住户和社区商户按角色入驻，所有权限都围绕小区、事项和数据域授权。</p>
        </div>
        <div class="registration-entry-grid">
          <button v-for="item in registrationRoles" :key="item.title" @click="item.action === 'community' ? openPublicRegistration('community') : item.action === 'resident' ? openResidentRegistrationHint() : openPublicRegistration('tenant', item.tenantType)">
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </button>
        </div>
      </section>

      <section id="resident-app" class="landing-section resident-app-section">
        <div class="resident-app-copy">
          <h2>住户一个 App / 小程序，办完小区生活</h2>
          <p>住户不需要在一堆群、表格和线下窗口里来回跑。缴费、报修、投票、公告、快递、餐饮、房屋租售、停车充电、门禁访客、便民服务都可以从同一个入口进入。</p>
          <div class="resident-feature-grid">
            <span v-for="item in residentAppFeatures" :key="item">{{ item }}</span>
          </div>
        </div>
        <div class="resident-phone">
          <div class="phone-top"></div>
          <h3>住户端</h3>
          <div class="phone-service-list">
            <span v-for="item in residentAppFeatures.slice(0, 8)" :key="item">{{ item }}</span>
          </div>
          <button @click="openResidentRegistrationHint">住户注册</button>
        </div>
      </section>

      <section id="merchant" class="landing-section merchant-section">
        <div class="merchant-copy">
          <h2>商户入驻：不是发广告，是进小区服务体系</h2>
          <p>餐饮、零售、家政、维修、养老托育、房屋服务都可以接进来，但必须先完成主体资质、服务范围、小区授权和投诉评价闭环。住户看到的是可信服务，物业和政府看到的是可监管运营。</p>
          <div class="hero-actions merchant-actions">
            <el-button type="primary" size="large" @click="openPublicRegistration('tenant', 'MERCHANT')">申请商户入驻</el-button>
            <el-button size="large" plain @click="scrollToLanding('ecosystem')">查看生态能力</el-button>
          </div>
        </div>
        <div class="merchant-scenario-grid">
          <article v-for="item in merchantScenarios" :key="item.title">
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <section id="ecosystem" class="landing-section">
        <div class="landing-section-head ecosystem-head">
          <h2>社区服务生态操作系统：让城市服务有序进入小区</h2>
          <p>SPARK Nexus 不做无边界的功能堆叠，而是把每一种外部能力转化为“可准入、可授权、可监管、可结算、可评价、可退出”的社区服务资产。</p>
        </div>
        <div class="ecosystem-rule-strip">
          <article v-for="item in ecosystemRules" :key="item.title">
            <strong>{{ item.title }}</strong>
            <span>{{ item.text }}</span>
          </article>
        </div>
        <div class="ecosystem-grid">
          <article v-for="item in ecosystemCapabilities" :key="item.title">
            <span>{{ item.code }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </section>

      <section class="landing-section philosophy-section">
        <div class="philosophy-intro">
          <h2>平台理念</h2>
          <p>SPARK Nexus 做的不是单点工具，而是城市社区数字基础设施：先建立治理秩序，再承接生活服务，再沉淀可监管、可运营、可持续的社区数字经济。</p>
        </div>
        <div class="strategy-grid">
          <article v-for="item in strategicPillars" :key="item.title">
            <span>{{ item.tag }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
        <div class="trust-grid">
          <article v-for="item in trustFoundations" :key="item.code">
            <span>{{ item.code }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.text }}</p>
          </article>
        </div>
        <ul class="principle-list">
          <li v-for="item in platformPrinciples" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="landing-section onboarding-section">
        <article v-for="item in onboardingSteps" :key="item.step">
          <span>{{ item.step }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
        </article>
      </section>
    </main>

    <el-dialog v-model="publicRegistrationVisible" :title="publicRegistrationMode === 'community' ? '小区备案申请' : '主体入驻申请'" width="560px">
      <el-form v-if="publicRegistrationMode === 'tenant'" label-position="top">
        <el-form-item label="主体类型">
          <el-segmented v-model="tenantRegistrationForm.tenantType" :options="tenantTypeOptions" />
        </el-form-item>
        <el-form-item label="主体名称">
          <el-input v-model="tenantRegistrationForm.tenantName" />
        </el-form-item>
        <el-form-item label="统一社会信用代码">
          <el-input v-model="tenantRegistrationForm.unifiedCreditCode" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="tenantRegistrationForm.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="tenantRegistrationForm.contactPhone" />
        </el-form-item>
        <el-form-item label="管理员账号">
          <el-input v-model="tenantRegistrationForm.adminUsername" />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input v-model="tenantRegistrationForm.adminPassword" />
        </el-form-item>
        <el-form-item label="管理员姓名">
          <el-input v-model="tenantRegistrationForm.adminDisplayName" />
        </el-form-item>
      </el-form>
      <el-form v-else label-position="top">
        <el-form-item label="行政区">
          <el-input v-model="communityRegistrationForm.district" />
        </el-form-item>
        <el-form-item label="街道">
          <el-input v-model="communityRegistrationForm.street" />
        </el-form-item>
        <el-form-item label="社区">
          <el-input v-model="communityRegistrationForm.neighborhood" />
        </el-form-item>
        <el-form-item label="小区名称">
          <el-input v-model="communityRegistrationForm.name" />
        </el-form-item>
        <el-form-item label="户数">
          <el-input-number v-model="communityRegistrationForm.households" :min="0" :step="100" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="communityRegistrationForm.contactPhone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publicRegistrationVisible = false">取消</el-button>
        <el-button type="primary" @click="publicRegistrationMode === 'community' ? submitCommunityRegistration() : submitTenantRegistration()">提交申请</el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">SN</span>
        <div>
          <strong>SPARK Nexus</strong>
          <small>城市物业治理中枢</small>
        </div>
      </div>
      <button v-for="[key, label] in navItems" :key="key" :class="{ active: active === key }" @click="active = key">
        {{ label }}
      </button>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ title }}</h1>
          <p>{{ roleName }} · Nexus 数据域按组织、小区与授权范围隔离</p>
        </div>
        <div class="top-actions">
          <el-input placeholder="搜索小区、流水、凭证" class="search" />
          <el-select v-if="user.tenants?.length" v-model="activeTenantId" class="tenant-switch" @change="changeTenant">
            <el-option v-for="tenant in user.tenants" :key="tenant.tenantId" :label="tenant.tenantName" :value="tenant.tenantId" />
          </el-select>
          <el-tag type="success">{{ user.displayName }}</el-tag>
          <el-button @click="logout">退出</el-button>
        </div>
      </header>

      <section v-loading="loading" class="content">
        <section v-if="!navItems.length" class="panel">
          <div class="panel-head"><h2>当前账号无管理端权限</h2><span>请使用业主端访问业主功能</span></div>
        </section>
        <template v-if="active === 'dashboard' && data.dashboard">
          <div class="metric-grid">
            <article v-for="metric in data.dashboard.metrics" :key="metric.label" class="metric-card">
              <span>{{ metric.label }}</span>
              <strong>{{ money(metric.value) }}<em>{{ metric.unit }}</em></strong>
              <small>{{ metric.trend }}</small>
            </article>
          </div>
          <div class="dashboard-grid">
            <section class="panel wide">
              <div class="panel-head">
                <h2>Nexus 治理态势</h2>
                <span>公共收益、收费、审批按月汇聚</span>
              </div>
              <div ref="chartEl" class="chart"></div>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>风险预警</h2><span>规则扫描与处置闭环</span><el-button size="small" type="primary" @click="scanSupervisionAlerts">扫描预警</el-button><el-button size="small" @click="downloadSupervisionAlertsCsv">导出台账</el-button></div>
              <div v-for="alert in data.dashboard.alerts" :key="alert.id" class="alert-row">
                <el-tag :type="alert.level === 'HIGH' ? 'danger' : alert.level === 'MEDIUM' ? 'warning' : 'info'">{{ alert.level }}</el-tag>
                <div>
                  <strong>{{ alert.title }}</strong>
                  <p>{{ alert.communityName }} · {{ alert.description }}</p>
                  <div class="row-actions">
                    <el-tag :type="statusType(alert.status)">{{ alert.status }}</el-tag>
                    <el-button size="small" @click="updateAlertStatus(alert, 'TRACKING')">跟踪</el-button>
                    <el-button size="small" type="success" @click="updateAlertStatus(alert, 'CLOSED')">关闭</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-if="!data.dashboard.alerts?.length" description="暂无未读预警" />
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>监管规则</h2><span>大额支出、长期未公示、缴费率偏低</span></div>
            <el-table :data="data.alertRules" height="220">
              <el-table-column prop="ruleName" label="规则" />
              <el-table-column prop="ruleCode" label="编码" />
              <el-table-column label="级别"><template #default="{ row }"><el-tag :type="row.level === 'HIGH' ? 'danger' : 'warning'">{{ row.level }}</el-tag></template></el-table-column>
              <el-table-column label="金额/比例阈值"><template #default="{ row }">{{ row.thresholdAmount ? money(row.thresholdAmount) : '-' }}</template></el-table-column>
              <el-table-column label="天数阈值"><template #default="{ row }">{{ row.thresholdDays || '-' }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>信用/审计视图</h2><span>按小区沉淀风险评分</span><el-button size="small" type="primary" @click="generateCreditScores">生成评分</el-button><el-button size="small" @click="downloadCreditScoresCsv">导出评分</el-button></div>
            <el-table :data="data.creditScores" height="260">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column label="评分"><template #default="{ row }"><strong>{{ money(row.score) }}</strong></template></el-table-column>
              <el-table-column label="等级"><template #default="{ row }"><el-tag :type="row.riskGrade === 'A' ? 'success' : row.riskGrade === 'B' ? 'warning' : 'danger'">{{ row.riskGrade }}</el-tag></template></el-table-column>
              <el-table-column prop="paymentRate" label="缴费率%" />
              <el-table-column prop="openAlertCount" label="未关闭预警" />
              <el-table-column prop="overdueBillCount" label="欠费账单" />
              <el-table-column prop="auditStatus" label="审计链" />
              <el-table-column prop="summary" label="摘要" min-width="260" />
              <el-table-column label="操作" width="90"><template #default="{ row }"><el-button size="small" @click="openCreditFactors(row)">因子</el-button></template></el-table-column>
            </el-table>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>信用评分规则</h2><span>版本化权重与扣分公式</span><el-button size="small" type="primary" @click="downloadCreditRulesCsv">导出规则</el-button></div>
              <el-table :data="data.creditRules" height="240">
                <el-table-column prop="ruleVersion" label="版本" width="90" />
                <el-table-column prop="factorName" label="因子" />
                <el-table-column prop="weight" label="权重" width="80" />
                <el-table-column prop="thresholdValue" label="阈值" width="90" />
                <el-table-column prop="deductionUnit" label="扣分单位" width="100" />
                <el-table-column prop="evidenceTemplate" label="证据口径" min-width="220" />
                <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>评分运行批次</h2><span>记录每次生成使用的规则版本</span></div>
              <el-table :data="data.creditRuns" height="240">
                <el-table-column prop="ruleVersion" label="规则" width="90" />
                <el-table-column prop="generatedCount" label="生成数" width="90" />
                <el-table-column prop="operator" label="操作人" width="100" />
                <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
                <el-table-column prop="summary" label="摘要" min-width="180" />
              </el-table>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>辖区小区排名</h2><span>可下钻账户与流水</span></div>
            <el-table :data="data.dashboard.ranks" height="260">
              <el-table-column prop="name" label="小区" min-width="160" />
              <el-table-column prop="street" label="街道" />
              <el-table-column prop="households" label="户数" />
              <el-table-column prop="occupancyRate" label="入住率%" />
              <el-table-column label="账户余额"><template #default="{ row }">¥{{ money(row.balance) }}</template></el-table-column>
              <el-table-column prop="paymentRate" label="缴费率%" />
              <el-table-column label="操作" width="110"><template #default="{ row }"><el-button size="small" type="primary" @click="openCommunityDrilldown(row)">下钻</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-if="active === 'screen' && data.dashboard">
          <section class="screen-board">
            <div class="screen-hero">
              <div>
                <span>SPARK Nexus 城市物业治理监管大屏</span>
                <h2>辖区资金、审批、预警一屏总览</h2>
              </div>
              <div class="row-actions">
                <el-button @click="downloadScreenTopicsCsv">导出专题</el-button>
                <el-button type="primary" @click="load">刷新数据</el-button>
              </div>
            </div>
            <div class="screen-metrics">
              <article v-for="metric in data.dashboard.metrics" :key="metric.label">
                <span>{{ metric.label }}</span>
                <strong>{{ money(metric.value) }}<em>{{ metric.unit }}</em></strong>
                <small>{{ metric.trend }}</small>
              </article>
            </div>
            <div class="screen-layout">
              <section class="screen-panel screen-chart">
                <div class="screen-panel-head"><h3>收支趋势</h3><span>公共收益月度流向</span></div>
                <div ref="chartEl" class="screen-chart-body"></div>
              </section>
              <section class="screen-panel">
                <div class="screen-panel-head"><h3>风险预警</h3><span>{{ data.dashboard.alerts?.length || 0 }} 条待处置</span></div>
                <div v-for="alert in data.dashboard.alerts" :key="alert.id" class="screen-alert">
                  <el-tag :type="alert.level === 'HIGH' ? 'danger' : alert.level === 'MEDIUM' ? 'warning' : 'info'">{{ alert.level }}</el-tag>
                  <div>
                    <strong>{{ alert.title }}</strong>
                    <p>{{ alert.communityName }} · {{ alert.description }}</p>
                  </div>
                </div>
                <el-empty v-if="!data.dashboard.alerts?.length" description="暂无预警" />
              </section>
              <section class="screen-panel">
                <div class="screen-panel-head"><h3>资金穿透</h3><span>小区账户与缴费率</span></div>
                <div v-for="rank in data.dashboard.ranks" :key="rank.id" class="screen-rank" @click="openCommunityDrilldown(rank)">
                  <strong>{{ rank.name }}</strong>
                  <span>{{ rank.street }} · 余额 ¥{{ money(rank.balance) }}</span>
                  <em>缴费率 {{ rank.paymentRate }}%</em>
                </div>
              </section>
              <section class="screen-panel">
                <div class="screen-panel-head"><h3>信用/审计</h3><span>风险等级</span></div>
                <div v-for="score in data.creditScores" :key="score.id" class="screen-credit">
                  <strong>{{ score.communityName }}</strong>
                  <el-tag :type="score.riskGrade === 'A' ? 'success' : score.riskGrade === 'B' ? 'warning' : 'danger'">{{ score.riskGrade }}</el-tag>
                  <span>{{ money(score.score) }} 分 · {{ score.auditStatus }}</span>
                </div>
                <el-button class="scan-button" type="primary" @click="generateCreditScores">生成评分</el-button>
              </section>
              <section class="screen-panel screen-wide">
                <div class="screen-panel-head"><h3>专题分析</h3><span>{{ data.screenTopics?.length || 0 }} 个专题切片</span></div>
                <div class="topic-grid">
                  <article v-for="topic in data.screenTopics" :key="`${topic.topicCode}-${topic.communityName}`" class="topic-card">
                    <div>
                      <strong>{{ topic.topicName }}</strong>
                      <el-tag :type="topic.level === 'HIGH' ? 'danger' : topic.level === 'MEDIUM' ? 'warning' : 'success'">{{ topic.level }}</el-tag>
                    </div>
                    <span>{{ topic.communityName }} · {{ topic.headline }}</span>
                    <p>{{ topic.trend }}：{{ money(topic.primaryValue) }} / {{ money(topic.secondaryValue) }}</p>
                    <em>{{ topic.evidence }}</em>
                    <small>{{ topic.action }}</small>
                  </article>
                </div>
              </section>
            </div>
          </section>
        </template>

        <template v-if="active === 'communities'">
          <section class="panel">
            <div class="panel-head"><h2>小区档案</h2><span>行政区/街道/社区/阈值</span></div>
            <el-table :data="data.communities">
              <el-table-column prop="district" label="行政区" />
              <el-table-column prop="street" label="街道" />
              <el-table-column prop="neighborhood" label="社区" />
              <el-table-column prop="name" label="小区" />
              <el-table-column prop="households" label="户数" />
              <el-table-column label="审批阈值"><template #default="{ row }">¥{{ money(row.approvalThreshold) }}</template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>房屋与业主</h2><span>实名绑定基础数据</span></div>
            <el-table :data="data.houses" height="260">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="building" label="楼栋" />
              <el-table-column prop="roomNo" label="房号" />
              <el-table-column prop="area" label="面积" />
              <el-table-column prop="ownerName" label="业主" />
              <el-table-column prop="ownerPhone" label="手机号" />
              <el-table-column prop="status" label="状态" />
            </el-table>
          </section>
        </template>

        <template v-if="active === 'billing'">
          <div class="dashboard-grid">
            <section v-if="canWriteBilling" class="panel">
              <div class="panel-head"><h2>收费标准配置</h2><span>按小区、类型、计费模式配置</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="feeForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="费用类型">
                  <el-select v-model="feeForm.feeType">
                    <el-option label="物业费" value="物业费" />
                    <el-option label="停车费" value="停车费" />
                    <el-option label="水电费" value="水电费" />
                  </el-select>
                </el-form-item>
                <el-form-item label="计费模式">
                  <el-segmented v-model="feeForm.billingMode" :options="[{ label: '按面积', value: 'AREA' }, { label: '固定金额', value: 'FIXED' }]" />
                </el-form-item>
                <el-form-item label="单价/金额">
                  <el-input-number v-model="feeForm.unitPrice" :precision="2" :min="0" :step="0.1" />
                </el-form-item>
                <el-button type="primary" @click="createFeeStandard">保存收费标准</el-button>
              </el-form>
            </section>
            <section v-if="canWriteBilling" class="panel">
              <div class="panel-head"><h2>自动生成账单</h2><span>按月批量生成，避免重复生成</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="generationForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="账期">
                  <el-input v-model="generationForm.period" placeholder="例如 2026-07" />
                </el-form-item>
                <el-form-item label="缴费截止日">
                  <el-date-picker v-model="generationForm.dueDate" value-format="YYYY-MM-DD" type="date" />
                </el-form-item>
                <el-button type="success" @click="generateBills">生成账单</el-button>
              </el-form>
            </section>
            <section v-if="canWriteBilling" class="panel">
              <div class="panel-head"><h2>欠费公开</h2><span>生成脱敏公示台账并同步业主端</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="arrearsPublicationForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="账期">
                  <el-input v-model="arrearsPublicationForm.period" placeholder="留空则公示全部欠费" />
                </el-form-item>
                <el-form-item label="公示标题">
                  <el-input v-model="arrearsPublicationForm.title" />
                </el-form-item>
                <el-button type="warning" @click="createArrearsPublication">生成欠费公示</el-button>
              </el-form>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>当前收费标准</h2><span>用于账单自动计算</span></div>
            <el-table :data="data.feeStandards" height="220">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="feeType" label="类型" />
              <el-table-column label="模式"><template #default="{ row }">{{ row.billingMode === 'AREA' ? '按面积' : '固定金额' }}</template></el-table-column>
              <el-table-column label="单价/金额"><template #default="{ row }">¥{{ money(row.unitPrice) }}</template></el-table-column>
              <el-table-column prop="cycle" label="周期" />
              <el-table-column prop="effectiveFrom" label="生效日期" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>账单收费</h2><span>物业费、停车费、水电费</span><el-button size="small" type="primary" @click="downloadBillingCsv">批量导出</el-button></div>
            <el-table :data="data.billing">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="roomNo" label="房号" />
              <el-table-column prop="billType" label="类型" />
              <el-table-column prop="period" label="期间" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="320">
                <template #default="{ row }">
                  <el-button v-if="canWritePayment && row.status !== 'PAID'" size="small" type="primary" @click="prepay(row, 'wechat')">微信</el-button>
                  <el-button v-if="canWritePayment && row.status !== 'PAID'" size="small" type="success" @click="prepay(row, 'alipay')">支付宝</el-button>
                  <el-button v-if="canWritePayment && row.status === 'PAID'" size="small" type="primary" plain @click="issueBillReceipt(row)">票据</el-button>
                  <el-button v-if="canWritePayment && row.status === 'PAID'" size="small" type="warning" @click="refundBill(row, 'wechat')">微信退</el-button>
                  <el-button v-if="canWritePayment && row.status === 'PAID'" size="small" type="warning" plain @click="refundBill(row, 'alipay')">支付宝退</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>退款记录</h2><span>微信/支付宝退款、负向银行流水、短信通知</span></div>
            <el-table :data="data.paymentRefunds" height="260">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="roomNo" label="房号" />
              <el-table-column prop="billType" label="类型" />
              <el-table-column prop="period" label="期间" />
              <el-table-column prop="payChannel" label="渠道" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column prop="reason" label="原因" min-width="150" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="providerRefundNo" label="渠道退款号" min-width="210" />
              <el-table-column prop="processedAt" label="处理时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>支付账单同步</h2><span>渠道交易账单、退款账单与平台订单匹配</span></div>
            <el-form class="inline-form compact-form" label-position="top">
              <el-form-item label="账单日期">
                <el-date-picker v-model="paymentStatementForm.statementDate" value-format="YYYY-MM-DD" />
              </el-form-item>
              <el-button type="primary" @click="syncPaymentStatement('wechat')">同步微信账单</el-button>
              <el-button type="success" @click="syncPaymentStatement('alipay')">同步支付宝账单</el-button>
            </el-form>
            <el-table :data="data.paymentStatements" height="260">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="provider" label="渠道" />
              <el-table-column prop="statementDate" label="账单日" />
              <el-table-column prop="tradeType" label="类型" />
              <el-table-column prop="billId" label="账单ID" />
              <el-table-column prop="orderNo" label="平台单号" min-width="150" />
              <el-table-column prop="channelTradeNo" label="渠道单号" min-width="220" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="syncedAt" label="同步时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>电子缴费票据</h2><span>缴费后自动开具，支持PDF与台账导出</span><el-button size="small" type="primary" @click="downloadPaymentReceiptsCsv">导出台账</el-button></div>
            <el-table :data="data.paymentReceipts" height="260">
              <el-table-column prop="receiptNo" label="票据号" min-width="170" />
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="roomNo" label="房号" />
              <el-table-column prop="payerName" label="缴款人" />
              <el-table-column prop="billType" label="类型" />
              <el-table-column prop="period" label="账期" />
              <el-table-column prop="payChannel" label="渠道" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column prop="checksum" label="校验码" min-width="120" />
              <el-table-column prop="issuedAt" label="开具时间" width="170" />
              <el-table-column label="操作" width="160"><template #default="{ row }"><el-button size="small" @click="downloadPaymentReceiptPdf(row)">PDF</el-button><el-button size="small" type="success" @click="issueTaxInvoice(row)">税票</el-button></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>税务发票台账</h2><span>数电票适配层、税局流水、红冲留痕</span><el-button size="small" type="primary" @click="downloadTaxInvoicesCsv">导出台账</el-button></div>
            <el-table :data="data.taxInvoices" height="260">
              <el-table-column prop="invoiceRequestNo" label="申请号" min-width="180" />
              <el-table-column prop="receiptNo" label="电子票据号" min-width="160" />
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="buyerName" label="购方" />
              <el-table-column prop="buyerTaxNo" label="税号" min-width="120" />
              <el-table-column prop="invoiceItem" label="项目" />
              <el-table-column prop="taxCategoryCode" label="分类编码" min-width="120" />
              <el-table-column label="税率"><template #default="{ row }">{{ Math.round(Number(row.taxRate || 0) * 10000) / 100 }}%</template></el-table-column>
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="taxInvoiceNo" label="数电票号" min-width="150" />
              <el-table-column prop="taxPlatformCode" label="平台流水" min-width="150" />
              <el-table-column prop="issuedAt" label="开具时间" width="170" />
              <el-table-column label="操作" width="150"><template #default="{ row }"><el-button size="small" @click="downloadTaxInvoicePdf(row)">PDF</el-button><el-button v-if="row.status === 'ISSUED'" size="small" type="warning" @click="redCancelTaxInvoice(row)">红冲</el-button></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>欠费公开台账</h2><span>脱敏房号、欠费金额、导出留档</span></div>
            <el-table :data="data.arrearsPublications" height="260">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="period" label="账期" />
              <el-table-column prop="title" label="标题" min-width="190" />
              <el-table-column prop="arrearsCount" label="欠费笔数" />
              <el-table-column label="欠费合计"><template #default="{ row }">¥{{ money(row.arrearsAmount) }}</template></el-table-column>
              <el-table-column prop="publishedBy" label="发布人" />
              <el-table-column prop="publishedAt" label="发布时间" width="170" />
              <el-table-column label="操作" width="160"><template #default="{ row }"><el-button size="small" @click="openArrearsItems(row)">明细</el-button><el-button size="small" type="primary" @click="downloadArrearsCsv(row)">导出</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-if="active === 'revenue'">
          <section class="panel">
            <div class="panel-head"><h2>银行流水</h2><span>公共收益与物业账户分离</span></div>
            <el-table :data="data.revenue">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="accountName" label="账户" min-width="210" />
              <el-table-column prop="direction" label="方向" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column prop="counterparty" label="对方户名" />
              <el-table-column prop="summary" label="摘要" min-width="180" />
              <el-table-column prop="traceNo" label="流水号" />
            </el-table>
          </section>
        </template>

        <template v-if="active === 'expenses'">
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>公共支出申请</h2><span>报销单/付款单，发票与合同留痕</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="expenseForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="单据类型">
                  <el-segmented v-model="expenseForm.orderType" :options="[{ label: '付款单', value: 'PAYMENT' }, { label: '报销单', value: 'REIMBURSEMENT' }]" />
                </el-form-item>
                <el-form-item label="事项标题">
                  <el-input v-model="expenseForm.title" />
                </el-form-item>
                <el-form-item label="金额">
                  <el-input-number v-model="expenseForm.amount" :precision="2" :min="0" :step="1000" />
                </el-form-item>
                <el-form-item label="发票号">
                  <el-input v-model="expenseForm.invoiceNo" />
                </el-form-item>
                <el-form-item label="开票方">
                  <el-input v-model="expenseForm.invoiceIssuer" />
                </el-form-item>
                <el-form-item label="发票金额">
                  <el-input-number v-model="expenseForm.invoiceAmount" :precision="2" :min="0" :step="1000" />
                </el-form-item>
                <el-form-item label="开票日期">
                  <el-date-picker v-model="expenseForm.invoiceIssueDate" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-form-item label="合同编号">
                  <el-input v-model="expenseForm.contractNo" />
                </el-form-item>
                <el-form-item label="承包/供应方">
                  <el-input v-model="expenseForm.contractParty" />
                </el-form-item>
                <el-form-item label="合同金额">
                  <el-input-number v-model="expenseForm.contractAmount" :precision="2" :min="0" :step="1000" />
                </el-form-item>
                <el-form-item label="合同签署日">
                  <el-date-picker v-model="expenseForm.contractSignDate" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-button type="primary" @click="createExpense">提交支出申请</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>审批规则</h2><span>阈值决定流程节点</span></div>
              <el-form class="inline-form compact-form" label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="approvalRuleForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="社区审批阈值">
                  <el-input-number v-model="approvalRuleForm.expenseThreshold" :precision="2" :min="0" :step="5000" />
                </el-form-item>
                <el-form-item label="表决阈值">
                  <el-input-number v-model="approvalRuleForm.voteThreshold" :precision="2" :min="0" :step="5000" />
                </el-form-item>
                <el-form-item label="超时小时">
                  <el-input-number v-model="approvalRuleForm.approvalTimeoutHours" :min="1" :step="1" />
                </el-form-item>
                <el-form-item label="表决通过率%">
                  <el-input-number v-model="approvalRuleForm.voteRatio" :precision="2" :min="0" :max="100" :step="1" />
                </el-form-item>
                <el-button type="primary" @click="saveApprovalRule">保存规则</el-button>
              </el-form>
              <el-table :data="data.approvalRules" height="250">
                <el-table-column prop="communityName" label="小区" />
                <el-table-column label="社区审批阈值"><template #default="{ row }">¥{{ money(row.expenseThreshold) }}</template></el-table-column>
                <el-table-column label="表决阈值"><template #default="{ row }">¥{{ money(row.voteThreshold) }}</template></el-table-column>
                <el-table-column prop="approvalTimeoutHours" label="超时小时" />
                <el-table-column prop="voteRatio" label="表决通过率%" />
              </el-table>
              <el-button class="scan-button" type="warning" @click="scanTimeoutExpenses">扫描超时支出</el-button>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head">
              <h2>工作流模板</h2>
              <span>模板节点、条件分支与事件留痕</span>
              <el-button size="small" @click="newWorkflowNode">新增节点</el-button>
              <el-button size="small" type="primary" @click="downloadWorkflowEventsCsv">导出事件</el-button>
            </div>
            <div class="workflow-template-strip">
              <div v-for="item in data.workflowTemplates || []" :key="item.id" class="metric-tile">
                <strong>{{ item.templateName }}</strong>
                <span>{{ item.templateCode }} · {{ item.businessType }} · {{ item.status }}</span>
              </div>
            </div>
            <el-form class="inline-form compact-form workflow-node-form" label-position="top">
              <el-form-item label="模板">
                <el-select v-model="workflowTemplateCode">
                  <el-option v-for="item in data.workflowTemplates || []" :key="item.templateCode" :label="item.templateName" :value="item.templateCode" />
                </el-select>
              </el-form-item>
              <el-form-item label="节点编码">
                <el-input v-model="workflowNodeForm.nodeCode" />
              </el-form-item>
              <el-form-item label="节点名称">
                <el-input v-model="workflowNodeForm.nodeName" />
              </el-form-item>
              <el-form-item label="处理角色">
                <el-select v-model="workflowNodeForm.roleCode">
                  <el-option label="业委会" value="COMMITTEE" />
                  <el-option label="街道/社区" value="STREET" />
                  <el-option label="政府监管" value="GOVERNMENT" />
                  <el-option label="业主" value="OWNER" />
                  <el-option label="银行" value="BANK" />
                </el-select>
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="workflowNodeForm.sortNo" :min="1" :step="1" />
              </el-form-item>
              <el-form-item label="触发条件">
                <el-select v-model="workflowNodeForm.conditionCode">
                  <el-option label="始终触发" value="ALWAYS" />
                  <el-option label="达到社区审批阈值" value="AMOUNT_GTE_THRESHOLD" />
                  <el-option label="达到业主表决阈值" value="OWNER_VOTE" />
                </el-select>
              </el-form-item>
              <el-form-item label="超时小时">
                <el-input-number v-model="workflowNodeForm.timeoutHours" :min="1" :step="1" />
              </el-form-item>
              <el-form-item label="状态">
                <el-segmented v-model="workflowNodeForm.status" :options="[{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'DISABLED' }]" />
              </el-form-item>
              <el-button type="primary" @click="saveWorkflowNode">保存节点</el-button>
            </el-form>
            <el-table :data="workflowTemplateNodes" height="260">
              <el-table-column prop="sortNo" label="#" width="54" />
              <el-table-column prop="nodeName" label="节点" min-width="140" />
              <el-table-column prop="nodeCode" label="编码" min-width="150" />
              <el-table-column prop="roleCode" label="角色" width="120" />
              <el-table-column prop="conditionCode" label="触发条件" width="160" />
              <el-table-column prop="timeoutHours" label="超时小时" width="100" />
              <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="90"><template #default="{ row }"><el-button size="small" @click="editWorkflowNode(row)">编辑</el-button></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>公共支出审批</h2><span>大额支出三级联审，超时自动预警</span></div>
            <el-table :data="data.expenses">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="orderType" label="单据类型" />
              <el-table-column prop="title" label="事项" min-width="190" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="currentNode" label="当前节点" />
              <el-table-column label="资料" width="90"><template #default="{ row }"><el-button size="small" @click="openExpenseDocuments(row)">台账</el-button></template></el-table-column>
              <el-table-column label="操作" width="330"><template #default="{ row }"><el-button size="small" @click="openApprovalNodes(row)">节点</el-button><el-button size="small" @click="openWorkflowEvents(row)">事件</el-button><el-button v-if="row.status === 'PENDING' || row.status === 'WARNING'" size="small" type="success" @click="decide(row, 'approve')">通过</el-button><el-button v-if="row.status === 'PENDING' || row.status === 'WARNING'" size="small" type="danger" @click="decide(row, 'reject')">驳回</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-if="active === 'votes'">
          <section class="panel">
            <div class="panel-head"><h2>发布投票问卷</h2><span>支持实名表决与问卷调查</span></div>
            <el-form class="inline-form" label-position="top">
              <el-form-item label="小区">
                <el-select v-model="voteForm.communityId">
                  <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型">
                <el-segmented v-model="voteForm.voteType" :options="[{ label: '表决', value: 'VOTE' }, { label: '问卷', value: 'SURVEY' }]" />
              </el-form-item>
              <el-form-item label="标题">
                <el-input v-model="voteForm.title" />
              </el-form-item>
              <el-form-item label="开始时间">
                <el-date-picker v-model="voteForm.startAt" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" />
              </el-form-item>
              <el-form-item label="结束时间">
                <el-date-picker v-model="voteForm.endAt" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" />
              </el-form-item>
              <el-form-item v-if="voteForm.voteType === 'SURVEY'" label="问卷题目">
                <el-input v-model="voteForm.questionTitle" />
              </el-form-item>
              <el-form-item v-if="voteForm.voteType === 'SURVEY'" label="选项">
                <el-input v-model="voteForm.optionsText" type="textarea" :rows="3" />
              </el-form-item>
              <el-button type="primary" @click="createVote">发布</el-button>
            </el-form>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>投票问卷</h2><span>实名表决与参与率统计</span></div>
            <el-table :data="data.votes">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="title" label="标题" min-width="220" />
              <el-table-column prop="voteType" label="类型" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="participationRate" label="参与率%" />
              <el-table-column prop="agreeRate" label="同意率%" />
              <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.voteType === 'SURVEY'" size="small" type="primary" @click="openSurveyResults(row)">结果</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-if="active === 'repairs'">
          <div class="dashboard-grid">
            <section v-if="user.role !== 'MERCHANT'" class="panel">
              <div class="panel-head"><h2>发布公告</h2><span>同步到业主端与站内消息</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="announcementForm.communityId">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="类别">
                  <el-select v-model="announcementForm.category">
                    <el-option label="通知公告" value="通知公告" />
                    <el-option label="财务公示" value="财务公示" />
                    <el-option label="审批公示" value="审批公示" />
                    <el-option label="风险提示" value="风险提示" />
                  </el-select>
                </el-form-item>
                <el-form-item label="标题">
                  <el-input v-model="announcementForm.title" />
                </el-form-item>
                <el-form-item label="内容">
                  <el-input v-model="announcementForm.content" type="textarea" :rows="4" />
                </el-form-item>
                <el-button type="primary" @click="createAnnouncement">发布公告</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>站内通知</h2><span>缴费、审批、工单处理提醒</span></div>
              <div v-for="message in data.messages || []" :key="message.id" class="message-row">
                <div>
                  <strong>{{ message.title }}</strong>
                  <p>{{ message.content }}</p>
                </div>
                <el-tag>{{ message.channel }}</el-tag>
              </div>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head">
              <h2>报修与投诉</h2>
              <span>业主服务闭环</span>
              <el-button v-if="user.role !== 'MERCHANT'" type="warning" @click="scanWorkOrderSla">扫描 SLA</el-button>
            </div>
            <el-table :data="[...(data.repairs || []), ...(data.complaints || [])]">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="roomNo" label="房号" />
              <el-table-column prop="orderType" label="类型" />
              <el-table-column prop="priority" label="优先级" width="100" />
              <el-table-column prop="title" label="标题" />
              <el-table-column prop="description" label="描述" min-width="240" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="dueAt" label="承诺截止" width="180" />
              <el-table-column label="评价" width="120"><template #default="{ row }">{{ row.satisfactionScore ? `${row.satisfactionScore} 星` : '待评价' }}</template></el-table-column>
              <el-table-column prop="reply" label="回复" min-width="180" />
              <el-table-column label="操作" width="110"><template #default="{ row }"><el-button size="small" type="primary" @click="openReplyDialog(row)">处理</el-button></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>SLA 规则</h2><span>报修/投诉承诺处理时限</span></div>
            <el-table :data="data.slaRules">
              <el-table-column prop="orderType" label="类型" />
              <el-table-column prop="priority" label="优先级" />
              <el-table-column prop="responseHours" label="响应小时" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>公告公示</h2><span>业主端同步展示</span></div>
            <el-table :data="data.announcements">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="category" label="类别" />
              <el-table-column prop="title" label="标题" min-width="200" />
              <el-table-column prop="content" label="内容" min-width="280" />
              <el-table-column prop="publishedAt" label="发布时间" width="180" />
            </el-table>
          </section>
        </template>

        <template v-if="active === 'finance'">
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>账套启用</h2><span>按小区管理公共收益账套</span></div>
              <el-table :data="data.accountBooks" height="240">
                <el-table-column prop="communityName" label="小区" />
                <el-table-column prop="bookName" label="账套名称" min-width="190" />
                <el-table-column prop="enabledMonth" label="启用月份" />
                <el-table-column prop="baseCurrency" label="币种" />
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>会计科目</h2><span>收入、支出、资产、负债科目</span></div>
              <el-table :data="data.subjects" height="240">
                <el-table-column prop="subjectCode" label="编码" />
                <el-table-column prop="subjectName" label="科目" min-width="180" />
                <el-table-column prop="subjectType" label="类型" />
                <el-table-column label="方向"><template #default="{ row }"><el-tag :type="row.direction === 'DEBIT' ? 'success' : 'warning'">{{ row.direction === 'DEBIT' ? '借方' : '贷方' }}</el-tag></template></el-table-column>
              </el-table>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>初始余额</h2><span>账套启用时点科目余额</span></div>
            <el-table :data="data.initialBalances">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="openingMonth" label="启用月份" />
              <el-table-column prop="subjectCode" label="科目编码" />
              <el-table-column prop="subjectName" label="科目名称" min-width="190" />
              <el-table-column label="方向"><template #default="{ row }"><el-tag :type="row.direction === 'DEBIT' ? 'success' : 'warning'">{{ row.direction === 'DEBIT' ? '借方' : '贷方' }}</el-tag></template></el-table-column>
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>财务凭证</h2>
              <span>自动制证、手动新增、批量审核、批量记账、导出PDF</span>
              <div class="actions">
                <el-button size="small" type="success" @click="batchReviewVouchers('approve')">批量审核</el-button>
                <el-button size="small" type="warning" @click="batchBookVouchers">批量记账</el-button>
              </div>
            </div>
            <el-form class="inline-form compact-form voucher-form" label-position="top">
              <el-form-item label="小区">
                <el-select v-model="voucherForm.communityId">
                  <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="借方科目">
                <el-select v-model="voucherForm.debitSubject" filterable>
                  <el-option v-for="item in data.subjects || []" :key="`debit-${item.id}`" :label="item.subjectName" :value="item.subjectName" />
                </el-select>
              </el-form-item>
              <el-form-item label="贷方科目">
                <el-select v-model="voucherForm.creditSubject" filterable>
                  <el-option v-for="item in data.subjects || []" :key="`credit-${item.id}`" :label="item.subjectName" :value="item.subjectName" />
                </el-select>
              </el-form-item>
              <el-form-item label="金额">
                <el-input-number v-model="voucherForm.amount" :precision="2" :min="0" :step="1000" />
              </el-form-item>
              <el-button type="primary" @click="createManualVoucher">新增凭证</el-button>
            </el-form>
            <el-table :data="data.vouchers" @selection-change="handleVoucherSelection">
              <el-table-column type="selection" width="48" />
              <el-table-column prop="voucherNo" label="凭证号" />
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="sourceType" label="来源" />
              <el-table-column prop="debitSubject" label="借方科目" />
              <el-table-column prop="creditSubject" label="贷方科目" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="280">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'REVIEWING'" size="small" type="success" @click="reviewVoucher(row, 'approve')">审核通过</el-button>
                  <el-button v-if="row.status === 'REVIEWING'" size="small" type="danger" @click="reviewVoucher(row, 'reject')">驳回</el-button>
                  <el-button v-if="row.status === 'APPROVED'" size="small" type="warning" @click="bookVoucher(row)">记账</el-button>
                  <el-button size="small" type="primary" @click="downloadVoucherPdf(row)">导出PDF</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>总分类账</h2><span>已记账凭证借贷明细</span><el-button size="small" @click="downloadFinancePdf('ledger')">导出PDF</el-button><el-button size="small" type="primary" @click="downloadFinanceCsv('ledger')">导出CSV</el-button></div>
              <el-table :data="data.ledger" height="320">
                <el-table-column prop="communityName" label="小区" />
                <el-table-column prop="voucherNo" label="凭证号" />
                <el-table-column prop="subject" label="科目" min-width="180" />
                <el-table-column label="方向"><template #default="{ row }"><el-tag :type="row.direction === 'DEBIT' ? 'success' : 'warning'">{{ row.direction === 'DEBIT' ? '借方' : '贷方' }}</el-tag></template></el-table-column>
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>现金流量表</h2><span>来自银行流水汇总</span><el-button size="small" @click="downloadFinancePdf('cash-flow')">导出PDF</el-button><el-button size="small" type="primary" @click="downloadFinanceCsv('cash-flow')">导出CSV</el-button></div>
              <el-table :data="data.cashFlow" height="320">
                <el-table-column prop="communityName" label="小区" />
                <el-table-column prop="item" label="项目" min-width="170" />
                <el-table-column label="方向"><template #default="{ row }"><el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ row.direction === 'IN' ? '流入' : '流出' }}</el-tag></template></el-table-column>
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              </el-table>
            </section>
          </div>
        </template>

        <template v-if="active === 'banking'">
          <section v-if="data.bankWorkbench" class="panel">
            <div class="panel-head">
              <h2>银行端工作台</h2>
              <span>以小区为中心汇总代收、对账、放款待办</span>
            </div>
            <div class="metric-grid bank-metric-grid">
              <article class="metric-card">
                <span>服务小区</span>
                <strong>{{ bankWorkbench.allowedCommunityCount || 0 }}<em>个</em></strong>
                <small>{{ bankWorkbench.activeConfigCount || 0 }} 项银行服务已启用</small>
              </article>
              <article class="metric-card">
                <span>今日入账</span>
                <strong>¥{{ money(bankWorkbench.todayInAmount) }}</strong>
                <small>今日出账 ¥{{ money(bankWorkbench.todayOutAmount) }}</small>
              </article>
              <article class="metric-card">
                <span>待放款</span>
                <strong>{{ bankWorkbench.pendingDisbursementCount || 0 }}<em>笔</em></strong>
                <small>合计 ¥{{ money(bankWorkbench.pendingDisbursementAmount) }}</small>
              </article>
              <article class="metric-card">
                <span>待处理差异</span>
                <strong>{{ bankWorkbench.openReconciliationDetailCount || 0 }}<em>条</em></strong>
                <small>{{ bankWorkbench.diffReconciliationCount || 0 }} 笔对账未平</small>
              </article>
              <article class="metric-card">
                <span>适配器预警</span>
                <strong>{{ bankWorkbench.adapterWarningCount || 0 }}<em>项</em></strong>
                <small>接口契约、签名、回调、放款配置</small>
              </article>
            </div>
          </section>
          <div v-if="data.bankWorkbench" class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>银行端待办</h2><span>放款与对账差异优先处理</span></div>
              <el-table :data="bankTodos" height="260">
                <el-table-column label="类型" width="120">
                  <template #default="{ row }"><el-tag :type="row.todoType === 'DISBURSEMENT' ? 'warning' : 'danger'">{{ row.todoType === 'DISBURSEMENT' ? '放款' : '对账' }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="communityName" label="小区" min-width="160" />
                <el-table-column prop="businessNo" label="业务号" min-width="180" />
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
                <el-table-column prop="createdAt" label="创建时间" width="170" />
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>服务小区监控</h2><span>近30日银行流水与最新对账</span></div>
              <el-table :data="bankCommunitySummaries" height="260">
                <el-table-column prop="communityName" label="小区" min-width="150" />
                <el-table-column label="入账"><template #default="{ row }">¥{{ money(row.inAmount) }}</template></el-table-column>
                <el-table-column label="出账"><template #default="{ row }">¥{{ money(row.outAmount) }}</template></el-table-column>
                <el-table-column label="对账"><template #default="{ row }"><el-tag :type="statusType(row.latestReconciliationStatus || 'OPEN')">{{ row.latestReconciliationStatus || '未对账' }}</el-tag></template></el-table-column>
              </el-table>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head">
              <h2>多银行直连适配</h2>
              <span>接口契约、签名算法、对账和放款模式</span>
              <div class="actions">
                <el-button size="small" type="primary" @click="runBankAdapterHealthCheck">健康检查</el-button>
                <el-button size="small" @click="downloadBankAdapterHealthCsv">导出检查</el-button>
              </div>
            </div>
            <el-table :data="data.bankAdapters" height="260">
              <el-table-column prop="bankCode" label="编码" width="130" />
              <el-table-column prop="bankName" label="银行" min-width="190" />
              <el-table-column prop="bankTenantName" label="机构租户" min-width="160" />
              <el-table-column prop="signAlgorithm" label="签名" width="120" />
              <el-table-column prop="callbackAlgorithm" label="回调验签" width="120" />
              <el-table-column prop="statementMode" label="对账模式" width="140" />
              <el-table-column prop="disbursementMode" label="放款模式" width="150" />
              <el-table-column label="检查" width="110"><template #default="{ row }"><el-tag :type="statusType(row.lastCheckResult || row.status)">{{ row.lastCheckResult || row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="lastEvidence" label="最近证据" min-width="260" />
            </el-table>
          </section>
          <div class="dashboard-grid">
            <section v-if="user.role === 'BANK'" class="panel">
              <div class="panel-head"><h2>申请开通小区银行服务</h2><span>银行入驻后按小区申请代收或监管账户服务</span></div>
              <el-form label-position="top">
                <el-form-item label="服务小区">
                  <el-select v-model="bankServiceApplicationForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务类型">
                  <el-segmented v-model="bankServiceApplicationForm.serviceType" :options="[{ label: '缴费代收', value: 'COLLECTION' }, { label: '资金监管', value: 'SUPERVISION' }]" />
                </el-form-item>
                <el-form-item label="商户号/监管编号">
                  <el-input v-model="bankServiceApplicationForm.merchantNo" />
                </el-form-item>
                <el-button type="primary" @click="submitBankServiceApplication">提交服务开通申请</el-button>
              </el-form>
            </section>
            <section v-if="canManageBankConfig" class="panel">
              <div class="panel-head"><h2>银行服务配置</h2><span>小区代收、监管账户服务</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="bankConfigForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="银行机构">
                  <el-select v-model="bankConfigForm.bankTenantId">
                    <el-option v-for="item in (data.tenants || []).filter((tenant: AnyRow) => tenant.tenantType === 'BANK')" :key="item.id" :label="item.tenantName" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务类型">
                  <el-segmented v-model="bankConfigForm.serviceType" :options="[{ label: '缴费代收', value: 'COLLECTION' }, { label: '资金监管', value: 'SUPERVISION' }]" />
                </el-form-item>
                <el-form-item label="商户号/监管编号">
                  <el-input v-model="bankConfigForm.merchantNo" />
                </el-form-item>
                <el-button type="primary" @click="createBankConfig">保存配置</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>执行对账</h2><span>按小区和日期比对支付订单与银行流水</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="reconciliationForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务类型">
                  <el-segmented v-model="reconciliationForm.serviceType" :options="[{ label: '缴费代收', value: 'COLLECTION' }, { label: '资金监管', value: 'SUPERVISION' }]" />
                </el-form-item>
                <el-form-item label="对账日期">
                  <el-date-picker v-model="reconciliationForm.reconcileDate" value-format="YYYY-MM-DD" type="date" />
                </el-form-item>
                <el-button type="primary" @click="runReconciliation">执行对账</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>导入银行流水</h2><span>银行回调/直连同步的人工联调入口</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="bankFlowImportForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="方向">
                  <el-segmented v-model="bankFlowImportForm.direction" :options="[{ label: '收入', value: 'IN' }, { label: '支出', value: 'OUT' }]" />
                </el-form-item>
                <el-form-item label="金额">
                  <el-input-number v-model="bankFlowImportForm.amount" :precision="2" :min="0" />
                </el-form-item>
                <el-form-item label="对方户名">
                  <el-input v-model="bankFlowImportForm.counterparty" />
                </el-form-item>
                <el-form-item label="摘要">
                  <el-input v-model="bankFlowImportForm.summary" />
                </el-form-item>
                <el-form-item label="发生时间">
                  <el-date-picker v-model="bankFlowImportForm.occurredAt" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" />
                </el-form-item>
                <el-form-item label="流水号">
                  <el-input v-model="bankFlowImportForm.traceNo" />
                </el-form-item>
                <el-button type="primary" @click="importBankFlow">导入流水</el-button>
              </el-form>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>银行配置列表</h2><span>已启用银行服务</span></div>
            <el-table :data="data.bankConfigs">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="bankTenantName" label="银行" min-width="180" />
              <el-table-column prop="serviceType" label="服务类型" />
              <el-table-column prop="merchantNo" label="商户号" />
              <el-table-column prop="fundAccountName" label="资金账户" min-width="220" />
              <el-table-column prop="status" label="状态" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>银行流水台账</h2><span>代收、退款、放款和人工导入流水</span><el-button size="small" @click="downloadBankFlowsCsv">导出CSV</el-button></div>
            <el-table :data="data.bankFlows" height="320">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="accountName" label="账户" min-width="180" />
              <el-table-column label="方向" width="90"><template #default="{ row }"><el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ row.direction }}</el-tag></template></el-table-column>
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column prop="counterparty" label="对方户名" min-width="140" />
              <el-table-column prop="summary" label="摘要" min-width="180" />
              <el-table-column prop="traceNo" label="流水号" min-width="160" />
              <el-table-column prop="occurredAt" label="发生时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>支出放款指令</h2><span>审批通过后进入银行直连放款</span></div>
            <el-table :data="data.bankDisbursements">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="instructionNo" label="指令号" min-width="180" />
              <el-table-column prop="payeeName" label="收款方" />
              <el-table-column prop="purpose" label="用途" min-width="180" />
              <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="bankTraceNo" label="银行流水号" min-width="150" />
              <el-table-column prop="processedBy" label="处理人" />
              <el-table-column label="操作" width="170">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'PENDING'" size="small" type="success" @click="processDisbursement(row, 'PAY')">放款</el-button>
                  <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="processDisbursement(row, 'RETURN')">退回</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>对账结果</h2><span>系统金额、银行金额、差异</span></div>
            <el-table :data="data.reconciliations">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="reconcileDate" label="日期" />
              <el-table-column prop="serviceType" label="服务" />
              <el-table-column label="系统金额"><template #default="{ row }">¥{{ money(row.systemAmount) }}</template></el-table-column>
              <el-table-column label="银行金额"><template #default="{ row }">¥{{ money(row.bankAmount) }}</template></el-table-column>
              <el-table-column label="差异"><template #default="{ row }">¥{{ money(row.diffAmount) }}</template></el-table-column>
              <el-table-column prop="matchedCount" label="匹配笔数" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.status === 'MATCHED' ? 'success' : 'danger'">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="100"><template #default="{ row }"><el-button size="small" @click="openReconciliationDetails(row)">明细</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-if="active === 'registrations'">
          <section class="panel">
            <div class="panel-head">
              <h2>小区中心五方+商户注册</h2>
              <span>围绕一个小区挂接物业、政府、业委会、银行、住户与社区商户</span>
              <div class="actions">
                <el-select v-model="registrationCenterCommunityId" class="tenant-switch" @change="changeRegistrationCommunity">
                  <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </div>
            </div>
            <div class="registration-party-grid">
              <article v-for="item in fivePartyCommunityStatus" :key="item.party">
                <span>{{ item.party }}</span>
                <strong>{{ item.owner }}</strong>
                <small>{{ item.status }}</small>
              </article>
            </div>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>主体入驻</h2><span>物业、政府、业委会、银行、商户先成为平台机构</span></div>
              <el-form label-position="top">
                <el-form-item label="机构类型">
                  <el-segmented v-model="tenantRegistrationForm.tenantType" :options="tenantTypeOptions" />
                </el-form-item>
                <el-form-item label="机构名称">
                  <el-input v-model="tenantRegistrationForm.tenantName" />
                </el-form-item>
                <el-form-item label="统一社会信用代码">
                  <el-input v-model="tenantRegistrationForm.unifiedCreditCode" />
                </el-form-item>
                <el-form-item label="联系人">
                  <el-input v-model="tenantRegistrationForm.contactName" />
                </el-form-item>
                <el-form-item label="联系电话">
                  <el-input v-model="tenantRegistrationForm.contactPhone" />
                </el-form-item>
                <el-form-item label="管理员账号">
                  <el-input v-model="tenantRegistrationForm.adminUsername" />
                </el-form-item>
                <el-form-item label="管理员密码">
                  <el-input v-model="tenantRegistrationForm.adminPassword" />
                </el-form-item>
                <el-form-item label="管理员姓名">
                  <el-input v-model="tenantRegistrationForm.adminDisplayName" />
                </el-form-item>
                <el-button type="primary" @click="submitTenantRegistration">提交注册申请</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>小区/业委会建档</h2><span>先形成小区主档，再挂接五方与商户关系</span></div>
              <el-form label-position="top">
                <el-form-item label="行政区">
                  <el-input v-model="communityRegistrationForm.district" />
                </el-form-item>
                <el-form-item label="街道">
                  <el-input v-model="communityRegistrationForm.street" />
                </el-form-item>
                <el-form-item label="社区">
                  <el-input v-model="communityRegistrationForm.neighborhood" />
                </el-form-item>
                <el-form-item label="小区名称">
                  <el-input v-model="communityRegistrationForm.name" />
                </el-form-item>
                <el-form-item label="户数">
                  <el-input-number v-model="communityRegistrationForm.households" :min="0" :step="100" />
                </el-form-item>
                <el-form-item label="联系电话">
                  <el-input v-model="communityRegistrationForm.contactPhone" />
                </el-form-item>
                <el-button type="primary" @click="submitCommunityRegistration">提交小区备案</el-button>
              </el-form>
            </section>
          </div>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>挂接小区服务关系</h2><span>物业、政府、业委会围绕当前小区授权</span></div>
              <el-form label-position="top">
                <el-form-item label="机构">
                  <el-select v-model="relationApplicationForm.tenantId">
                    <el-option v-for="item in data.tenants || []" :key="item.id" :label="item.tenantName" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="小区">
                  <el-select v-model="relationApplicationForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关系类型">
                  <el-select v-model="relationApplicationForm.relationType">
                    <el-option v-for="item in relationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="submitRelationApplication">提交关系申请</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>银行服务申请</h2><span>审核后自动生成银行配置、关系和授权</span></div>
              <el-form label-position="top">
                <el-form-item label="银行">
                  <el-select v-model="bankServiceApplicationForm.bankTenantId">
                    <el-option v-for="item in (data.tenants || []).filter((tenant: AnyRow) => tenant.tenantType === 'BANK')" :key="item.id" :label="item.tenantName" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="小区">
                  <el-select v-model="bankServiceApplicationForm.communityId">
                    <el-option v-for="item in communityOptions" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务类型">
                  <el-segmented v-model="bankServiceApplicationForm.serviceType" :options="[{ label: '缴费代收', value: 'COLLECTION' }, { label: '资金监管', value: 'SUPERVISION' }]" />
                </el-form-item>
                <el-form-item label="商户号">
                  <el-input v-model="bankServiceApplicationForm.merchantNo" />
                </el-form-item>
                <el-button type="primary" @click="submitBankServiceApplication">提交银行服务申请</el-button>
              </el-form>
            </section>
          </div>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>当前小区已挂接主体</h2><span>治理关系、商户服务与授权的主线视图</span></div>
              <el-table :data="registrationCommunityRelations" height="300">
                <el-table-column prop="tenantName" label="主体" min-width="180" />
                <el-table-column prop="tenantType" label="类型" />
                <el-table-column prop="relationType" label="关系" min-width="150" />
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>已入驻机构</h2><span>平台租户主档</span></div>
              <el-table :data="data.tenants" height="300">
                <el-table-column prop="tenantName" label="机构" min-width="180" />
                <el-table-column prop="tenantType" label="类型" />
                <el-table-column prop="contactName" label="联系人" />
                <el-table-column prop="status" label="状态" />
              </el-table>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>注册审核</h2><span>机构、小区、住户绑定统一审核</span><el-button size="small" @click="downloadRegistrationsCsv">导出台账</el-button></div>
            <el-table :data="data.registrations">
              <el-table-column prop="applicationNo" label="申请号" min-width="150" />
              <el-table-column prop="applicantType" label="申请类型" />
              <el-table-column prop="applicantName" label="申请主体" min-width="180" />
              <el-table-column prop="targetType" label="目标" />
              <el-table-column prop="payloadJson" label="资料" min-width="260" />
              <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="170">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'PENDING'" size="small" type="success" @click="reviewRegistration(row, 'approve')">通过</el-button>
                  <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="reviewRegistration(row, 'reject')">驳回</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>数据授权</h2><span>租户、小区、数据范围、动作</span><el-button size="small" @click="downloadAuthorizationsCsv">导出台账</el-button></div>
            <el-table :data="data.authorizations" height="320">
              <el-table-column prop="communityName" label="小区" />
              <el-table-column prop="granteeTenantName" label="被授权机构" min-width="180" />
              <el-table-column prop="dataScope" label="数据范围" />
              <el-table-column prop="permissionCode" label="权限" />
              <el-table-column prop="status" label="状态" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" @click="revokeAuthorization(row)">撤销</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>机构用户</h2><span>为物业、银行、政府机构创建管理员</span></div>
            <el-form class="inline-form" label-position="top">
              <el-form-item label="机构">
                <el-select v-model="tenantUserForm.tenantId" @change="loadTenantUsers">
                  <el-option v-for="item in data.tenants || []" :key="item.id" :label="item.tenantName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="账号">
                <el-input v-model="tenantUserForm.username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="tenantUserForm.password" />
              </el-form-item>
              <el-form-item label="姓名">
                <el-input v-model="tenantUserForm.displayName" />
              </el-form-item>
              <el-form-item label="角色">
                <el-select v-model="tenantUserForm.roleCode">
                  <el-option label="物业人员" value="PROPERTY" />
                  <el-option label="银行人员" value="BANK" />
                  <el-option label="政府监管" value="GOVERNMENT" />
                  <el-option label="街道审核" value="STREET" />
                  <el-option label="业委会" value="COMMITTEE" />
                  <el-option label="社区商户" value="MERCHANT" />
                </el-select>
              </el-form-item>
              <el-button type="primary" @click="createTenantUser">创建/绑定用户</el-button>
            </el-form>
            <el-table :data="data.tenantUsers" height="260">
              <el-table-column prop="username" label="账号" />
              <el-table-column prop="displayName" label="姓名" />
              <el-table-column prop="roleCode" label="角色" />
              <el-table-column prop="status" label="状态" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" @click="disableTenantUser(row)">停用</el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head"><h2>小区机构关系</h2><span>物业、政府、银行、商户与小区多对多绑定</span></div>
              <el-form label-position="top">
                <el-form-item label="小区">
                  <el-select v-model="relationForm.communityId" @change="loadCommunityRelations">
                    <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="机构">
                  <el-select v-model="relationForm.tenantId">
                    <el-option v-for="item in data.tenants || []" :key="item.id" :label="item.tenantName" :value="item.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关系类型">
                  <el-select v-model="relationForm.relationType">
                    <el-option label="物业服务" value="PROPERTY_SERVICE" />
                    <el-option label="政府管辖" value="JURISDICTION" />
                    <el-option label="政府监管" value="SUPERVISION" />
                    <el-option label="银行代收" value="BANK_COLLECTION" />
                    <el-option label="银行监管" value="BANK_SUPERVISION" />
                    <el-option label="业委会治理" value="COMMITTEE_GOVERN" />
                    <el-option label="本地生活商户" value="LOCAL_SERVICE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="开始日期">
                  <el-date-picker v-model="relationForm.startDate" value-format="YYYY-MM-DD" type="date" />
                </el-form-item>
                <el-button type="primary" @click="createCommunityRelation">绑定关系</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>当前小区关系</h2><span>按选中小区查看</span></div>
              <el-table :data="data.communityRelations" height="360">
                <el-table-column prop="tenantName" label="机构" min-width="180" />
                <el-table-column prop="tenantType" label="类型" />
                <el-table-column prop="relationType" label="关系" />
                <el-table-column prop="status" label="状态" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" @click="disableCommunityRelation(row)">停用</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>新增数据授权</h2><span>控制机构能看什么、能做什么</span></div>
            <el-form class="inline-form" label-position="top">
              <el-form-item label="授权方">
                <el-select v-model="authorizationForm.grantorTenantId">
                  <el-option v-for="item in data.tenants || []" :key="item.id" :label="item.tenantName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="被授权方">
                <el-select v-model="authorizationForm.granteeTenantId">
                  <el-option v-for="item in data.tenants || []" :key="item.id" :label="item.tenantName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="小区">
                <el-select v-model="authorizationForm.communityId">
                  <el-option v-for="item in data.communities || []" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="数据范围">
                <el-select v-model="authorizationForm.dataScopes" multiple collapse-tags>
                  <el-option label="小区档案" value="COMMUNITY_PROFILE" />
                  <el-option label="房屋" value="HOUSE" />
                  <el-option label="住户" value="RESIDENT" />
                  <el-option label="账单" value="BILLING" />
                  <el-option label="支付" value="PAYMENT" />
                  <el-option label="银行流水" value="BANK_FLOW" />
                  <el-option label="公共收益" value="PUBLIC_REVENUE" />
                  <el-option label="支出" value="EXPENSE" />
                  <el-option label="投诉" value="COMPLAINT" />
                  <el-option label="报修" value="REPAIR" />
                  <el-option label="投票" value="VOTE" />
                </el-select>
              </el-form-item>
              <el-form-item label="权限">
                <el-select v-model="authorizationForm.permissions" multiple collapse-tags>
                  <el-option label="读取" value="READ" />
                  <el-option label="写入" value="WRITE" />
                  <el-option label="审批" value="APPROVE" />
                  <el-option label="导出" value="EXPORT" />
                </el-select>
              </el-form-item>
              <el-button type="primary" @click="createAuthorization">创建授权</el-button>
            </el-form>
          </section>
        </template>

        <template v-if="active === 'audit'">
          <section class="panel" v-if="data.gatewayStatus">
            <div class="panel-head">
              <h2>API 网关状态</h2>
              <span>{{ data.gatewayStatus.gatewayMode }} · 统一前缀 {{ data.gatewayStatus.apiPrefix }}</span>
              <el-tag :type="data.gatewayStatus.status === 'UP' ? 'success' : 'danger'">{{ data.gatewayStatus.status }}</el-tag>
            </div>
            <div class="metric-grid compact">
              <article><span>公开路径</span><strong>{{ data.gatewayStatus.publicPaths?.length || 0 }}</strong></article>
              <article><span>路由组</span><strong>{{ data.gatewayStatus.routeGroups?.length || 0 }}</strong></article>
              <article><span>网关策略</span><strong>{{ data.gatewayStatus.policies?.length || 0 }}</strong></article>
            </div>
            <div class="dashboard-grid">
              <el-table :data="data.gatewayStatus.routeGroups" height="280">
                <el-table-column prop="moduleCode" label="模块" width="150" />
                <el-table-column prop="pathPattern" label="路径" min-width="260" />
                <el-table-column prop="description" label="说明" min-width="220" />
                <el-table-column label="角色" min-width="220"><template #default="{ row }">{{ row.roles?.join(' / ') }}</template></el-table-column>
              </el-table>
              <el-table :data="data.gatewayStatus.policies" height="280">
                <el-table-column prop="policyCode" label="策略" width="160" />
                <el-table-column prop="policyType" label="类型" width="150" />
                <el-table-column prop="description" label="说明" min-width="220" />
              </el-table>
            </div>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>外部对接状态</h2><span>真实适配器配置位 + dev 模拟</span></div>
            <div class="adapter-grid">
              <article v-for="adapter in data.integrations?.adapters || []" :key="adapter.code" class="adapter">
                <strong>{{ adapter.name }}</strong>
                <span>{{ adapter.code }}</span>
                <el-tag>{{ adapter.status }}</el-tag>
              </article>
            </div>
          </section>
          <section class="panel" v-if="data.productionReadiness">
            <div class="panel-head">
              <h2>生产上线门禁</h2>
              <span>按真实适配器配置、密钥强度、日志脱敏判定是否允许切生产</span>
              <el-tag :type="statusType(data.productionReadiness.status)">{{ data.productionReadiness.status }}</el-tag>
              <el-button size="small" @click="downloadProductionReadinessCsv">导出上线整改清单</el-button>
            </div>
            <div class="metric-grid compact">
              <article><span>当前模式</span><strong>{{ data.productionReadiness.mode }}</strong></article>
              <article><span>可切生产</span><strong>{{ data.productionReadiness.canSwitchProduction ? '是' : '否' }}</strong></article>
              <article><span>失败项</span><strong>{{ data.productionReadiness.failed }}</strong></article>
              <article><span>高危预警</span><strong>{{ data.productionReadiness.highWarnings }}</strong></article>
              <article><span>中危预警</span><strong>{{ data.productionReadiness.mediumWarnings }}</strong></article>
            </div>
          </section>
          <section class="panel" v-if="data.deploymentReadiness">
            <div class="panel-head">
              <h2>部署运行就绪</h2>
              <span>{{ data.deploymentReadiness.note }}</span>
              <el-tag :type="data.deploymentReadiness.status === 'READY' ? 'success' : data.deploymentReadiness.status === 'BLOCKED' ? 'danger' : 'warning'">{{ data.deploymentReadiness.status }}</el-tag>
              <el-button size="small" @click="downloadDeploymentReadinessCsv">导出部署检查</el-button>
            </div>
            <el-table :data="data.deploymentReadiness.items" height="260">
              <el-table-column prop="component" label="组件" min-width="170" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }"><el-tag :type="row.status === 'PASS' ? 'success' : row.status === 'FAIL' ? 'danger' : 'warning'">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column label="必需" width="90">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="endpoint" label="端点" min-width="220" />
              <el-table-column prop="evidence" label="证据" min-width="260" />
              <el-table-column prop="action" label="整改动作" min-width="320" />
            </el-table>
          </section>
          <section class="panel" v-if="data.databaseCompatibility">
            <div class="panel-head">
              <h2>国产数据库兼容矩阵</h2>
              <span>{{ data.databaseCompatibility.note }}</span>
              <el-tag :type="data.databaseCompatibility.status === 'PASS' ? 'success' : 'warning'">{{ data.databaseCompatibility.currentVendor }}</el-tag>
              <el-button size="small" @click="downloadDatabaseCompatibilityCsv">导出兼容整改清单</el-button>
            </div>
            <div class="metric-grid compact">
              <article><span>当前 JDBC</span><strong>{{ data.databaseCompatibility.datasourceUrl }}</strong></article>
              <article><span>数据库用户</span><strong>{{ data.databaseCompatibility.datasourceUsername || '-' }}</strong></article>
              <article><span>矩阵状态</span><strong>{{ data.databaseCompatibility.status }}</strong></article>
            </div>
            <el-table :data="data.databaseCompatibility.items" height="260">
              <el-table-column prop="vendorName" label="数据库" min-width="170" />
              <el-table-column prop="status" label="状态" width="110">
                <template #default="{ row }"><el-tag :type="row.status === 'CURRENT' ? 'success' : 'info'">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="checkResult" label="检查" width="110">
                <template #default="{ row }"><el-tag :type="row.checkResult === 'PASS' ? 'success' : 'warning'">{{ row.checkResult }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="jdbcPrefix" label="JDBC 前缀" min-width="160" />
              <el-table-column prop="driverClass" label="驱动类" min-width="190" />
              <el-table-column prop="flywayDialect" label="Flyway 方言" min-width="170" />
              <el-table-column prop="sqlCompatibility" label="SQL 兼容点" min-width="250" />
              <el-table-column prop="migrationAction" label="迁移动作" min-width="320" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>数据交换包</h2>
              <span>面向政府、银行、物业的数据域打包与校验摘要留档</span>
              <el-button size="small" type="primary" @click="createDataExchangePackage">生成交换包</el-button>
              <el-button size="small" @click="downloadDataExchangePackagesCsv">导出台账</el-button>
            </div>
            <el-table :data="data.dataExchangePackages || []" height="260">
              <el-table-column prop="packageNo" label="交换包编号" min-width="210" />
              <el-table-column prop="targetParty" label="目标方" width="120" />
              <el-table-column prop="communityName" label="小区" min-width="150" />
              <el-table-column prop="dataDomains" label="数据域" min-width="260" />
              <el-table-column prop="recordCount" label="记录数" width="100" />
              <el-table-column prop="checksum" label="校验摘要" min-width="160" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }"><el-tag type="success">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="createdBy" label="创建人" width="110" />
              <el-table-column prop="createdAt" label="创建时间" width="170" />
            </el-table>
          </section>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-head">
                <h2>适配器配置</h2>
                <span>银行、微信、支付宝、短信、实名配置入口</span>
                <el-button size="small" type="primary" @click="syncIntegrationConfigs">同步环境配置</el-button>
              </div>
              <el-table :data="data.integrationConfigs" height="280">
                <el-table-column prop="adapterCode" label="适配器" />
                <el-table-column prop="providerName" label="服务方" min-width="170" />
                <el-table-column prop="mode" label="模式" />
                <el-table-column prop="endpointUrl" label="地址" min-width="210" />
                <el-table-column prop="merchantNo" label="商户/编号" />
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>短信测试</h2><span>dev 模拟发送并写入调用日志</span></div>
              <el-form label-position="top">
                <el-form-item label="手机号">
                  <el-input v-model="smsTestForm.phone" />
                </el-form-item>
                <el-form-item label="内容">
                  <el-input v-model="smsTestForm.content" type="textarea" :rows="4" />
                </el-form-item>
                <el-button type="primary" @click="sendTestSms">发送测试短信</el-button>
              </el-form>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>实名认证核验</h2><span>业主身份、手机号、房屋档案掩码联调</span></div>
              <el-form label-position="top">
                <div class="form-grid two">
                  <el-form-item label="姓名">
                    <el-input v-model="identityTestForm.name" />
                  </el-form-item>
                  <el-form-item label="手机号">
                    <el-input v-model="identityTestForm.phone" />
                  </el-form-item>
                </div>
                <el-form-item label="身份证号">
                  <el-input v-model="identityTestForm.identityNo" />
                </el-form-item>
                <div class="form-grid two">
                  <el-form-item label="档案姓名">
                    <el-input v-model="identityTestForm.expectedName" />
                  </el-form-item>
                  <el-form-item label="档案手机号掩码">
                    <el-input v-model="identityTestForm.expectedPhoneMask" />
                  </el-form-item>
                </div>
                <el-form-item label="档案身份证掩码">
                  <el-input v-model="identityTestForm.expectedIdentityMask" />
                </el-form-item>
                <el-button type="primary" @click="runIdentityTest">核验实名信息</el-button>
              </el-form>
              <div v-if="data.identityTestResult" class="diagnostic-result">
                <el-tag :type="data.identityTestResult.verified ? 'success' : 'danger'">{{ data.identityTestResult.result }}</el-tag>
                <p><strong>姓名匹配</strong>{{ data.identityTestResult.nameMatched ? '是' : '否' }}</p>
                <p><strong>手机匹配</strong>{{ data.identityTestResult.phoneMatched ? '是' : '否' }}</p>
                <p><strong>证件匹配</strong>{{ data.identityTestResult.identityMatched ? '是' : '否' }}</p>
              </div>
            </section>
            <section class="panel">
              <div class="panel-head"><h2>回调验签联调</h2><span>按稳定字段排序生成签名载荷并验证渠道签名</span></div>
              <el-form label-position="top">
                <div class="form-grid two">
                  <el-form-item label="适配器">
                    <el-select v-model="callbackDiagnosticForm.provider">
                      <el-option label="微信支付" value="WECHAT_PAY" />
                      <el-option label="支付宝" value="ALIPAY" />
                      <el-option label="银行直连" value="BANK_DIRECT" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="事件">
                    <el-input v-model="callbackDiagnosticForm.eventType" />
                  </el-form-item>
                </div>
                <el-form-item label="业务编号">
                  <el-input v-model="callbackDiagnosticForm.businessNo" />
                </el-form-item>
                <el-form-item label="回调 JSON">
                  <el-input v-model="callbackDiagnosticForm.payloadText" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="渠道签名">
                  <el-input v-model="callbackDiagnosticForm.signature" />
                </el-form-item>
                <el-button type="primary" @click="runCallbackSignatureDiagnostics">验证签名</el-button>
              </el-form>
              <div v-if="data.callbackDiagnosticResult" class="diagnostic-result">
                <el-tag :type="data.callbackDiagnosticResult.verified ? 'success' : 'danger'">{{ data.callbackDiagnosticResult.signatureStatus }}</el-tag>
                <p><strong>Canonical</strong>{{ data.callbackDiagnosticResult.canonicalPayload }}</p>
                <p><strong>Signing Base</strong>{{ data.callbackDiagnosticResult.signingBase }}</p>
                <p><strong>期望签名</strong>{{ data.callbackDiagnosticResult.expectedSignature }}</p>
              </div>
            </section>
          </div>
          <section class="panel">
            <div class="panel-head"><h2>外部调用日志</h2><span>支付、银行、短信、实名全链路留痕</span></div>
            <el-table :data="data.integrationCalls" height="320">
              <el-table-column prop="adapterCode" label="适配器" />
              <el-table-column prop="operation" label="操作" />
              <el-table-column prop="status" label="状态" />
              <el-table-column prop="traceNo" label="追踪号" min-width="180" />
              <el-table-column prop="requestSummary" label="请求摘要" min-width="230" />
              <el-table-column prop="responseSummary" label="响应摘要" min-width="230" />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>外部回调回执</h2>
              <span>支付回调、银行放款回执的验签结果和处理摘要</span>
              <el-button size="small" @click="downloadExternalCallbacksCsv">导出回执台账</el-button>
            </div>
            <el-table :data="data.externalCallbacks" height="300">
              <el-table-column prop="adapterCode" label="适配器" width="130" />
              <el-table-column prop="eventType" label="事件" min-width="170" />
              <el-table-column prop="businessNo" label="业务编号" min-width="170" />
              <el-table-column label="验签" width="110">
                <template #default="{ row }"><el-tag :type="row.signatureStatus === 'VERIFIED' ? 'success' : 'danger'">{{ row.signatureStatus }}</el-tag></template>
              </el-table-column>
              <el-table-column label="处理" width="120">
                <template #default="{ row }"><el-tag :type="row.processStatus === 'PROCESSED' ? 'success' : row.processStatus === 'DUPLICATE' ? 'warning' : 'danger'">{{ row.processStatus }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="httpStatus" label="HTTP" width="90" />
              <el-table-column prop="responseSummary" label="处理摘要" min-width="230" />
              <el-table-column prop="idempotencyKey" label="幂等键" min-width="220" />
              <el-table-column prop="traceNo" label="追踪号" min-width="190" />
              <el-table-column prop="receivedAt" label="接收时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>回调防重放台账</h2>
              <span>支付/银行回调时间戳与 nonce 唯一校验留痕</span>
              <el-button size="small" @click="downloadExternalCallbackNoncesCsv">导出防重放台账</el-button>
            </div>
            <el-table :data="data.externalCallbackNonces" height="260">
              <el-table-column prop="adapterCode" label="适配器" width="130" />
              <el-table-column prop="eventType" label="事件" min-width="170" />
              <el-table-column prop="businessNo" label="业务编号" min-width="170" />
              <el-table-column prop="nonceValue" label="nonce" min-width="240" />
              <el-table-column prop="callbackTimestamp" label="回调时间戳" width="150" />
              <el-table-column prop="receivedAt" label="接收时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>敏感字段保护审计</h2>
              <span>手机号、证件号等字段的脱敏和核验留痕</span>
              <el-button size="small" @click="downloadSensitiveFieldAuditsCsv">导出保护审计</el-button>
            </div>
            <el-table :data="data.sensitiveFieldAudits" height="260">
              <el-table-column prop="targetTable" label="对象表" width="140" />
              <el-table-column prop="targetId" label="对象ID" width="90" />
              <el-table-column prop="fieldName" label="字段" width="130" />
              <el-table-column prop="protection" label="保护方式" min-width="170" />
              <el-table-column prop="actor" label="操作人" width="120" />
              <el-table-column prop="accessPurpose" label="访问用途" min-width="220" />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head">
              <h2>外部接口安全体检</h2>
              <span>生产配置、密钥强度、日志脱敏、dev 模式风险提示</span>
              <el-button size="small" type="primary" @click="runIntegrationSecurityChecks">运行体检</el-button>
              <el-button size="small" @click="downloadIntegrationSecurityCsv">导出整改清单</el-button>
            </div>
            <el-table :data="data.integrationSecurityChecks" height="320">
              <el-table-column prop="adapterCode" label="适配器" width="130" />
              <el-table-column prop="checkItem" label="检查项" min-width="170" />
              <el-table-column label="结果" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.checkResult === 'PASS' ? 'success' : row.checkResult === 'WARN' ? 'warning' : 'danger'">{{ row.checkResult }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="风险" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.riskLevel === 'LOW' ? 'success' : row.riskLevel === 'MEDIUM' ? 'warning' : 'danger'">{{ row.riskLevel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="evidence" label="证据" min-width="260" />
              <el-table-column prop="checkedAt" label="检查时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>数据导出台账</h2><span>CSV/PDF 导出按用户、租户、数据范围、筛选条件留痕</span></div>
            <el-table :data="data.exportLogs" height="300">
              <el-table-column prop="actor" label="导出人" width="110" />
              <el-table-column prop="roleCode" label="角色" width="110" />
              <el-table-column prop="exportModule" label="模块" width="150" />
              <el-table-column prop="dataScope" label="数据范围" width="180" />
              <el-table-column prop="targetType" label="对象" width="150" />
              <el-table-column prop="filterSummary" label="筛选摘要" min-width="260" />
              <el-table-column prop="fileName" label="文件" min-width="220" />
              <el-table-column prop="rowCount" label="行数" width="90" />
              <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              <el-table-column prop="createdAt" label="导出时间" width="170" />
            </el-table>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>审计日志</h2><span>关键操作留痕与哈希链</span><el-button size="small" type="primary" @click="verifyAuditChain">验证哈希链</el-button><el-button size="small" @click="downloadAuditCsv">导出CSV</el-button></div>
            <div v-if="data.auditVerification" class="audit-proof">
              <article>
                <span>链路状态</span>
                <strong :class="data.auditVerification.status === 'VALID' ? 'ok' : 'bad'">{{ data.auditVerification.status }}</strong>
              </article>
              <article>
                <span>日志总数</span>
                <strong>{{ data.auditVerification.totalCount }}</strong>
              </article>
              <article>
                <span>断链数量</span>
                <strong>{{ data.auditVerification.brokenLinks }}</strong>
              </article>
              <article>
                <span>首个断点</span>
                <strong>{{ data.auditVerification.firstBrokenId || '-' }}</strong>
              </article>
            </div>
            <el-table :data="data.audit">
              <el-table-column prop="actor" label="操作人" />
              <el-table-column prop="action" label="动作" min-width="190" />
              <el-table-column prop="targetType" label="对象" />
              <el-table-column prop="hash" label="哈希" />
              <el-table-column prop="previousHash" label="前序哈希" />
              <el-table-column prop="createdAt" label="时间" />
            </el-table>
          </section>
        </template>

        <template v-if="active === 'acceptance'">
          <section class="panel">
            <div class="panel-head">
              <h2>功能落地验收中心</h2>
              <span>按文档逐项核验注册制、多租户、监管、银行、住户服务闭环</span>
              <el-button size="small" @click="runAcceptanceDrill">运行验收演练</el-button>
              <el-button size="small" type="primary" @click="downloadAcceptanceCsv">导出CSV</el-button>
            </div>
            <div class="acceptance-summary" v-if="data.acceptance">
              <article>
                <span>完成度</span>
                <strong>{{ data.acceptance.completionScore }}%</strong>
              </article>
              <article>
                <span>已完成</span>
                <strong>{{ data.acceptance.doneCount }} / {{ data.acceptance.totalCount }}</strong>
              </article>
              <article>
                <span>验收口径</span>
                <strong>系统数据自动核验</strong>
              </article>
            </div>
          </section>
          <section class="panel">
            <div class="panel-head"><h2>逐项功能清单</h2><span>DONE 表示已有真实表、关系、记录或闭环数据支撑</span></div>
            <el-table :data="data.acceptance?.items || []" height="520">
              <el-table-column prop="module" label="模块" width="170" />
              <el-table-column prop="description" label="验收内容" min-width="260" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'DONE' ? 'success' : row.status === 'PARTIAL' ? 'warning' : 'danger'">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="证据数" width="120">
                <template #default="{ row }">{{ row.matchedEvidenceCount }} / {{ row.requiredEvidenceCount }}</template>
              </el-table-column>
              <el-table-column label="缺口摘要" min-width="220">
                <template #default="{ row }">{{ row.gapSummary }}</template>
              </el-table-column>
              <el-table-column label="下一步" min-width="260">
                <template #default="{ row }">{{ row.nextAction }}</template>
              </el-table-column>
              <el-table-column label="数据证据" min-width="260">
                <template #default="{ row }">
                  <el-tooltip v-for="(count, index) in row.evidenceCounts" :key="index" :content="row.evidenceLabels?.[index] || `证据${index + 1}`">
                    <el-tag class="evidence-tag" :type="count > 0 ? 'success' : 'info'">{{ count }}</el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </template>
      </section>
    </main>

    <el-drawer v-model="drilldownVisible" size="72%" :title="drilldown?.community?.name || '小区监管下钻'">
      <div v-loading="drilldownLoading" class="drilldown">
        <section v-if="drilldown?.community" class="drill-summary">
          <article>
            <span>行政区划</span>
            <strong>{{ drilldown.community.district }} / {{ drilldown.community.street }}</strong>
          </article>
          <article>
            <span>入住率</span>
            <strong>{{ drilldown.community.occupancyRate }}%</strong>
          </article>
          <article>
            <span>审批阈值</span>
            <strong>¥{{ money(drilldown.community.approvalThreshold) }}</strong>
          </article>
          <article>
            <span>待缴账单</span>
            <strong>{{ drilldown.overdueBills?.length || 0 }} 笔</strong>
          </article>
        </section>

        <section class="panel">
          <div class="panel-head"><h2>资金账户</h2><span>公共收益专户与物业服务账户</span></div>
          <el-table :data="drilldown?.accounts || []" height="220">
            <el-table-column prop="accountType" label="账户类型" />
            <el-table-column prop="accountName" label="账户名称" min-width="220" />
            <el-table-column prop="bankName" label="开户行" />
            <el-table-column prop="accountNoMask" label="账号" />
            <el-table-column label="余额"><template #default="{ row }">¥{{ money(row.balance) }}</template></el-table-column>
          </el-table>
        </section>

        <section class="panel">
          <div class="panel-head"><h2>银行流水</h2><span>资金穿透明细</span></div>
          <el-table :data="drilldown?.flows || []" height="260">
            <el-table-column prop="accountName" label="账户" min-width="220" />
            <el-table-column label="方向"><template #default="{ row }"><el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ row.direction === 'IN' ? '收入' : '支出' }}</el-tag></template></el-table-column>
            <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
            <el-table-column prop="counterparty" label="对方户名" />
            <el-table-column prop="summary" label="摘要" min-width="180" />
            <el-table-column prop="traceNo" label="流水号" />
          </el-table>
        </section>

        <section class="panel">
          <div class="panel-head"><h2>凭证与审批</h2><span>支出、凭证、欠费关联核查</span></div>
          <el-tabs>
            <el-tab-pane label="财务凭证">
              <el-table :data="drilldown?.vouchers || []" height="240">
                <el-table-column prop="voucherNo" label="凭证号" />
                <el-table-column prop="sourceType" label="来源" />
                <el-table-column prop="debitSubject" label="借方科目" min-width="180" />
                <el-table-column prop="creditSubject" label="贷方科目" min-width="180" />
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="审批记录">
              <el-table :data="drilldown?.expenses || []" height="240">
                <el-table-column prop="title" label="事项" min-width="210" />
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
                <el-table-column prop="currentNode" label="当前节点" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="待缴账单">
              <el-table :data="drilldown?.overdueBills || []" height="240">
                <el-table-column prop="roomNo" label="房号" />
                <el-table-column prop="billType" label="类型" />
                <el-table-column prop="period" label="账期" />
                <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
                <el-table-column label="状态"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="reconciliationDetailVisible" title="对账明细" width="860px">
      <section class="panel">
        <div class="panel-head">
          <h2>{{ selectedReconciliation?.communityName || '小区' }}</h2>
          <div class="actions">
            <span>{{ selectedReconciliation?.reconcileDate }} · {{ selectedReconciliation?.status }}</span>
            <el-button size="small" @click="downloadReconciliationDetailsCsv">导出明细</el-button>
          </div>
        </div>
        <el-table :data="data.reconciliationDetails || []" height="420">
          <el-table-column prop="sourceType" label="来源" />
          <el-table-column prop="orderNo" label="订单号" min-width="160" />
          <el-table-column prop="traceNo" label="流水号" min-width="160" />
          <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
          <el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.status === 'MATCHED' ? 'success' : 'danger'">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="处理" width="120"><template #default="{ row }"><el-tag :type="statusType(row.handledStatus)">{{ row.handledStatus }}</el-tag></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" />
          <el-table-column prop="handleRemark" label="处理说明" min-width="180" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'MATCHED' && row.handledStatus === 'OPEN'" size="small" type="success" @click="resolveReconciliationDetail(row, 'RESOLVED')">处理</el-button>
              <el-button v-if="row.status !== 'MATCHED' && row.handledStatus === 'OPEN'" size="small" @click="resolveReconciliationDetail(row, 'IGNORED')">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>

    <el-dialog v-model="approvalNodeVisible" title="审批节点明细" width="760px">
      <div v-if="selectedExpense" class="dialog-summary">
        <strong>{{ selectedExpense.title }}</strong>
        <span>{{ selectedExpense.communityName }} · ¥{{ money(selectedExpense.amount) }} · {{ selectedExpense.status }}</span>
      </div>
      <el-table :data="approvalNodes">
        <el-table-column prop="sortNo" label="#" width="54" />
        <el-table-column prop="nodeName" label="节点" min-width="130" />
        <el-table-column prop="roleCode" label="处理角色" width="110" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="operator" label="处理人" width="110" />
        <el-table-column prop="comment" label="说明" min-width="180" />
        <el-table-column prop="operatedAt" label="处理时间" width="170" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="workflowEventVisible" title="工作流事件轨迹" width="920px">
      <div v-if="selectedExpense" class="dialog-summary">
        <strong>{{ selectedExpense.title }}</strong>
        <span>{{ selectedExpense.communityName }} · {{ selectedExpense.currentNode }} · {{ selectedExpense.status }}</span>
        <el-button size="small" type="primary" @click="downloadWorkflowEventsCsv">导出事件</el-button>
      </div>
      <el-table :data="workflowEvents" height="420">
        <el-table-column prop="templateCode" label="模板" width="130" />
        <el-table-column prop="nodeCode" label="节点" min-width="140" />
        <el-table-column prop="eventType" label="事件" width="150" />
        <el-table-column prop="operator" label="操作人" width="130" />
        <el-table-column prop="eventSummary" label="摘要" min-width="260" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="expenseDocumentVisible" title="发票/合同资料台账" width="960px">
      <div v-if="selectedExpense" class="dialog-summary">
        <strong>{{ selectedExpense.title }}</strong>
        <span>{{ selectedExpense.communityName }} · {{ selectedExpense.currentNode }} · ¥{{ money(selectedExpense.amount) }}</span>
      </div>
      <el-form class="inline-form compact-form" label-position="top">
        <el-form-item label="类型">
          <el-select v-model="documentForm.documentType">
            <el-option label="发票" value="INVOICE" />
            <el-option label="合同" value="CONTRACT" />
            <el-option label="验收单" value="ACCEPTANCE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="编号">
          <el-input v-model="documentForm.documentNo" />
        </el-form-item>
        <el-form-item label="出具方">
          <el-input v-model="documentForm.issuer" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="documentForm.amount" :precision="2" :min="0" :step="1000" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="documentForm.issueDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="附件URL">
          <el-input v-model="documentForm.fileUrl" placeholder="MinIO/国产对象存储/线下扫描件地址" />
        </el-form-item>
        <el-button type="primary" @click="addExpenseDocument">追加资料</el-button>
      </el-form>
      <el-table :data="expenseDocuments" height="420">
        <el-table-column prop="documentType" label="类型" />
        <el-table-column prop="documentNo" label="编号" min-width="150" />
        <el-table-column prop="issuer" label="出具方" min-width="170" />
        <el-table-column label="金额"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
        <el-table-column prop="issueDate" label="日期" />
        <el-table-column label="核验状态"><template #default="{ row }"><el-tag :type="statusType(row.verificationStatus)">{{ row.verificationStatus }}</el-tag></template></el-table-column>
        <el-table-column prop="reviewComment" label="意见" min-width="160" />
        <el-table-column label="操作" width="230"><template #default="{ row }"><el-button size="small" @click="downloadExpenseDocument(row)">下载</el-button><el-button size="small" type="success" @click="verifyExpenseDocument(row, 'PASS')">通过</el-button><el-button size="small" type="danger" @click="verifyExpenseDocument(row, 'REJECT')">退回</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="arrearsDetailVisible" title="欠费公示明细" width="820px">
      <div v-if="selectedArrearsPublication" class="dialog-summary">
        <strong>{{ selectedArrearsPublication.title }}</strong>
        <span>{{ selectedArrearsPublication.communityName }} · {{ selectedArrearsPublication.period }} · ¥{{ money(selectedArrearsPublication.arrearsAmount) }}</span>
      </div>
      <el-table :data="arrearsItems" height="420">
        <el-table-column prop="roomNoMask" label="脱敏房号" />
        <el-table-column prop="billType" label="费用类型" />
        <el-table-column prop="period" label="期间" />
        <el-table-column label="应收"><template #default="{ row }">¥{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="已缴"><template #default="{ row }">¥{{ money(row.paidAmount) }}</template></el-table-column>
        <el-table-column label="欠费"><template #default="{ row }">¥{{ money(row.arrearsAmount) }}</template></el-table-column>
        <el-table-column prop="dueDate" label="截止日" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="surveyResultVisible" title="问卷统计结果" width="760px">
      <div v-if="selectedVote" class="dialog-summary">
        <strong>{{ selectedVote.title }}</strong>
        <span>{{ selectedVote.communityName }} · 参与率 {{ selectedVote.participationRate }}%</span>
      </div>
      <el-table :data="surveyResults">
        <el-table-column prop="questionTitle" label="题目" min-width="230" />
        <el-table-column prop="optionLabel" label="选项" />
        <el-table-column prop="responseCount" label="选择人数" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="creditFactorVisible" title="信用评分因子" width="860px">
      <div v-if="selectedCreditScore" class="dialog-summary">
        <strong>{{ selectedCreditScore.communityName }}</strong>
        <span>评分 {{ money(selectedCreditScore.score) }} · 等级 {{ selectedCreditScore.riskGrade }}</span>
        <el-button size="small" type="primary" @click="downloadCreditFactorsCsv">导出因子</el-button>
      </div>
      <el-table :data="creditFactors" height="420">
        <el-table-column prop="factorCode" label="编码" width="130" />
        <el-table-column prop="factorName" label="因子" />
        <el-table-column prop="factorValue" label="取值" />
        <el-table-column prop="weight" label="权重" />
        <el-table-column prop="deduction" label="扣分" />
        <el-table-column prop="evidence" label="证据说明" min-width="260" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="replyDialogVisible" title="处理工单" width="520px">
      <el-form label-position="top">
        <el-form-item label="工单">
          <el-input :model-value="selectedWorkOrder ? `${selectedWorkOrder.roomNo} · ${selectedWorkOrder.title}` : ''" disabled />
        </el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="workOrderReply.status" :options="[{ label: '处理中', value: 'PROCESSING' }, { label: '已完成', value: 'DONE' }, { label: '已关闭', value: 'CLOSED' }]" />
        </el-form-item>
        <el-form-item label="处理回复">
          <el-input v-model="workOrderReply.reply" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWorkOrderReply">提交处理结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>
