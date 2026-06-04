<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type AnyRow = Record<string, any>

const token = ref(uni.getStorageSync('token') || '')
const loginForm = ref({
  username: 'owner',
  password: 'admin123',
  name: '新业主',
  phone: '13800000009'
})
const profile = ref<AnyRow | null>(null)
const bills = ref<AnyRow[]>([])
const receipts = ref<AnyRow[]>([])
const flows = ref<AnyRow[]>([])
const announcements = ref<AnyRow[]>([])
const messages = ref<AnyRow[]>([])
const votes = ref<AnyRow[]>([])
const workOrders = ref<AnyRow[]>([])
const arrearsPublicItems = ref<AnyRow[]>([])
const surveyQuestions = ref<Record<number, AnyRow[]>>({})
const houseBindings = ref<AnyRow[]>([])
const houseBindingApplications = ref<AnyRow[]>([])
const activeHouseId = ref(Number(uni.getStorageSync('activeHouseId') || 0))
const ledger = ref<AnyRow>({})
const tab = ref('缴费')
const fallbackLoginAttempted = ref(false)
const apiBase = ((import.meta as unknown as { env?: Record<string, string> }).env?.VITE_API_BASE || uni.getStorageSync('apiBase') || '') as string
const workOrderForm = ref({
  orderType: 'REPAIR',
  priority: 'NORMAL',
  title: '',
  description: ''
})
const bindForm = ref({
  communityName: '鼎盛阳光一期',
  building: '1栋',
  roomNo: '1101',
  name: '张女士',
  phone: '13800001024',
  identityNo: '420100199001012381'
})

const unpaid = computed(() => bills.value.filter((bill) => bill.status !== 'PAID'))

function request<T>(url: string, options: UniApp.RequestOptions = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${apiBase}${url}`,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(token.value ? { Authorization: `Bearer ${token.value}` } : {})
      },
      success: (res) => {
        if (res.statusCode && res.statusCode >= 400) reject(new Error((res.data as AnyRow)?.message || '请求失败'))
        else resolve(res.data as T)
      },
      fail: reject
    })
  })
}

async function login() {
  const result = await request<AnyRow>('/api/auth/login', {
    method: 'POST',
    data: { username: loginForm.value.username, password: loginForm.value.password }
  })
  token.value = result.token
  uni.setStorageSync('token', token.value)
  await load()
}

async function registerOwner() {
  try {
    await request('/api/resident/register', {
      method: 'POST',
      data: {
        username: loginForm.value.username,
        password: loginForm.value.password,
        name: loginForm.value.name,
        phone: loginForm.value.phone
      }
    })
    uni.showToast({ title: '注册成功' })
    await login()
  } catch {
    uni.showToast({ title: '注册失败', icon: 'none' })
  }
}

async function load() {
  if (!token.value) return
  houseBindings.value = await request<AnyRow[]>('/api/resident/house-bindings/me').catch(() => [])
  houseBindingApplications.value = await request<AnyRow[]>('/api/resident/house-binding-applications/me').catch(() => [])
  const activeBinding = houseBindings.value.find((item) => item.houseId === activeHouseId.value)
  if (!activeBinding && houseBindings.value.length) {
    const primary = houseBindings.value.find((item) => item.isPrimary)
    activeHouseId.value = (primary || houseBindings.value[0]).houseId
    uni.setStorageSync('activeHouseId', activeHouseId.value)
  }
  if (!houseBindings.value.length) {
    const portal = await request<AnyRow>('/api/owners/portal').catch(() => null)
    if (portal) {
      applyPortal(portal)
      return
    }
    if (!fallbackLoginAttempted.value) {
      fallbackLoginAttempted.value = true
      token.value = ''
      activeHouseId.value = 0
      loginForm.value.username = 'owner'
      loginForm.value.password = 'admin123'
      uni.removeStorageSync('token')
      uni.removeStorageSync('activeHouseId')
      await login()
      return
    }
    activeHouseId.value = 0
    uni.removeStorageSync('activeHouseId')
    profile.value = {
      name: loginForm.value.name || '业主',
      communityName: '待绑定房屋',
      building: '',
      roomNo: ''
    }
    ledger.value = {}
    bills.value = []
    receipts.value = []
    flows.value = []
    announcements.value = []
    messages.value = []
    votes.value = []
    workOrders.value = []
    arrearsPublicItems.value = []
    surveyQuestions.value = {}
    tab.value = '我的'
    return
  }
  const portal = await request<AnyRow>(activeHouseId.value ? `/api/owners/portal?houseId=${activeHouseId.value}` : '/api/owners/portal')
  applyPortal(portal)
  await loadSurveyQuestions()
}

function applyPortal(portal: AnyRow) {
  profile.value = portal.profile
  ledger.value = portal.ledger || {}
  bills.value = portal.bills || []
  receipts.value = portal.receipts || []
  flows.value = portal.flows || []
  announcements.value = portal.announcements || []
  messages.value = portal.messages || []
  votes.value = portal.votes || []
  workOrders.value = portal.workOrders || []
  arrearsPublicItems.value = portal.arrearsPublicItems || []
}

const unreadCount = computed(() => messages.value.filter((item) => item.readStatus !== 'READ').length)

async function switchHouse(houseId: number) {
  activeHouseId.value = houseId
  uni.setStorageSync('activeHouseId', houseId)
  await load()
}

async function setDefaultHouse(binding: AnyRow) {
  await request(`/api/resident/house-bindings/${binding.id}/default`, { method: 'POST' })
  uni.showToast({ title: '已设为默认' })
  activeHouseId.value = binding.houseId
  uni.setStorageSync('activeHouseId', binding.houseId)
  await load()
}

async function pay(bill: AnyRow, channel: 'wechat' | 'alipay' = 'wechat') {
  const prepay = await request<AnyRow>(`/api/payments/${channel}/prepay`, {
    method: 'POST',
    data: { billId: bill.id }
  })
  await request(`/api/payments/${channel}/confirm`, {
    method: 'POST',
    data: { billId: bill.id, orderNo: prepay.orderNo, prepayId: prepay.prepayId, tradeNo: prepay.tradeNo }
  })
  uni.showToast({ title: channel === 'alipay' ? '支付宝缴费成功' : '微信缴费成功' })
  await load()
}

async function castVote(item: AnyRow, decision: 'AGREE' | 'DISAGREE') {
  try {
    await request(`/api/votes/${item.id}/cast`, {
      method: 'POST',
      data: { decision }
    })
    uni.showToast({ title: decision === 'AGREE' ? '已同意' : '已反对' })
    await load()
  } catch {
    uni.showToast({ title: '投票失败', icon: 'none' })
  }
}

async function loadSurveyQuestions() {
  const surveyVotes = votes.value.filter((item) => item.voteType === 'SURVEY')
  const loaded: Record<number, AnyRow[]> = {}
  for (const item of surveyVotes) {
    loaded[item.id] = await request<AnyRow[]>(`/api/votes/${item.id}/survey`).catch(() => [])
  }
  surveyQuestions.value = loaded
}

async function submitSurvey(vote: AnyRow, question: AnyRow, option: AnyRow) {
  try {
    await request(`/api/votes/${vote.id}/survey-submit`, {
      method: 'POST',
      data: { answers: [{ questionId: question.id, optionId: option.id }] }
    })
    uni.showToast({ title: '已提交问卷' })
    await load()
  } catch {
    uni.showToast({ title: '提交失败', icon: 'none' })
  }
}

async function submitWorkOrder() {
  if (!workOrderForm.value.title || !workOrderForm.value.description) {
    uni.showToast({ title: '请填写标题和描述', icon: 'none' })
    return
  }
  try {
    await request('/api/work-orders', {
      method: 'POST',
      data: { ...workOrderForm.value, houseId: activeHouseId.value || undefined }
    })
    uni.showToast({ title: '已提交' })
    workOrderForm.value.title = ''
    workOrderForm.value.description = ''
    workOrderForm.value.priority = 'NORMAL'
    await load()
  } catch {
    uni.showToast({ title: '提交失败', icon: 'none' })
  }
}

async function evaluateWorkOrder(item: AnyRow, score: number) {
  try {
    await request(`/api/work-orders/${item.id}/evaluate`, {
      method: 'POST',
      data: { score, comment: score >= 4 ? '处理满意' : '仍需改进' }
    })
    uni.showToast({ title: '已评价' })
    await load()
  } catch {
    uni.showToast({ title: '评价失败', icon: 'none' })
  }
}

async function bindHouse() {
  try {
    await request<AnyRow>('/api/resident/house-bindings', {
      method: 'POST',
      data: { ...bindForm.value, residentType: 'OWNER' }
    })
    uni.showToast({ title: '已提交审核' })
    houseBindings.value = await request<AnyRow[]>('/api/resident/house-bindings/me').catch(() => [])
    houseBindingApplications.value = await request<AnyRow[]>('/api/resident/house-binding-applications/me').catch(() => [])
    await load()
  } catch {
    uni.showToast({ title: '提交失败', icon: 'none' })
  }
}

async function markMessageRead(item: AnyRow) {
  if (item.readStatus === 'READ') return
  try {
    await request(`/api/messages/${item.id}/read`, { method: 'POST' })
    item.readStatus = 'READ'
    item.readAt = new Date().toISOString()
    uni.showToast({ title: '已读' })
  } catch {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function money(value: number | string) {
  return Number(value || 0).toFixed(2)
}

onMounted(async () => {
  const hadStoredToken = !!token.value
  try {
    if (!hadStoredToken) await login()
    else await load()
  } catch {
    token.value = ''
    activeHouseId.value = 0
    uni.removeStorageSync('token')
    uni.removeStorageSync('activeHouseId')
    try {
      await login()
    } catch {
      seedDemo()
    }
  }
})

function seedDemo() {
  profile.value = { name: '张女士', communityName: '鼎盛阳光一期', building: '1栋', roomNo: '1101' }
  bills.value = [
    { id: 1, billType: '物业费', period: '2026-06', communityName: '鼎盛阳光一期', roomNo: '1101', amount: 355.5, status: 'UNPAID' },
    { id: 2, billType: '停车费', period: '2026-06', communityName: '鼎盛阳光一期', roomNo: '1101', amount: 280, status: 'PAID' }
  ]
  receipts.value = [
    { id: 1, receiptNo: 'EPR-1-2-DEMO', billType: '停车费', period: '2026-06', payChannel: 'WECHAT_PAY', amount: 280, status: 'ISSUED', checksum: 'a18f9c2' }
  ]
  flows.value = [
    { id: 1, summary: '二季度电梯广告收益', accountName: '业委会公共收益专户', direction: 'IN', amount: 36000, traceNo: 'BK20260528001' },
    { id: 2, summary: '门禁改造付款', accountName: '业委会公共收益专户', direction: 'OUT', amount: 48500, traceNo: 'BK20260529002' }
  ]
  announcements.value = [{ id: 1, title: '2026年5月公共收益收支公示', category: '财务公示', content: '本月公共收益收入36,000元，支出48,500元，明细可在银行流水中查看。' }]
  messages.value = [{ id: 1, title: '缴费提醒', content: '您有2026-06物业费待缴，请及时处理。', channel: 'SMS_DEV' }]
  votes.value = [{ id: 1, title: '关于使用公共收益进行门禁改造的表决', voteType: 'VOTE', status: 'OPEN', participationRate: 62.3, agreeRate: 88.4 }]
  surveyQuestions.value = {}
  workOrders.value = [
    { id: 1, orderType: 'REPAIR', priority: 'URGENT', dueAt: '2026-06-02 10:00:00', title: '单元门禁无法刷卡', status: 'PROCESSING', roomNo: '1101', description: '1栋1单元门禁读卡失败，物业已派工程人员检修。' },
    { id: 2, orderType: 'COMPLAINT', priority: 'NORMAL', dueAt: '2026-06-04 18:00:00', title: '楼道杂物堆放', status: 'PENDING', roomNo: '1101', description: '2层楼道堆放杂物，请协调清理。' }
  ]
  arrearsPublicItems.value = [
    { id: 1, roomNoMask: '1**1', billType: '物业费', period: '2026-06', arrearsAmount: 355.5, dueDate: '2026-06-30' }
  ]
  ledger.value = { publicIncome: 36000, publicExpense: 48500, unpaidAmount: 355.5, paymentRate: 83.6 }
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view>
        <text class="eyebrow">SPARK Nexus 业主端</text>
        <text class="hello">{{ profile?.name || '业主' }}，您好</text>
        <text class="home">{{ profile?.communityName }} {{ profile?.building }} {{ profile?.roomNo }}</text>
      </view>
      <view class="amount">
        <text>Nexus 待办缴费</text>
        <strong>¥{{ money(unpaid.reduce((sum, item) => sum + Number(item.amount), 0)) }}</strong>
      </view>
    </view>

    <view class="tabs">
      <button v-for="item in ['缴费', '账务', '公告', '投票', '报修', '我的']" :key="item" :class="{ active: tab === item }" @click="tab = item">{{ item }}{{ item === '公告' && unreadCount ? `(${unreadCount})` : '' }}</button>
    </view>

    <view v-if="tab === '缴费'" class="list">
      <view v-for="bill in bills" :key="bill.id" class="card">
        <view><text class="title">{{ bill.billType }} · {{ bill.period }}</text><text class="muted">{{ bill.communityName }} {{ bill.roomNo }}</text></view>
        <view class="right">
          <strong>¥{{ money(bill.amount) }}</strong>
          <view v-if="bill.status !== 'PAID'" class="pay-row">
            <button @click="pay(bill, 'wechat')">微信</button>
            <button class="alipay" @click="pay(bill, 'alipay')">支付宝</button>
          </view>
          <text v-else class="paid">已缴</text>
        </view>
      </view>
    </view>

    <view v-if="tab === '账务'" class="list">
      <view class="stats">
        <view class="stat"><text>公共收益收入</text><strong>¥{{ money(ledger.publicIncome) }}</strong></view>
        <view class="stat"><text>公共收益支出</text><strong>¥{{ money(ledger.publicExpense) }}</strong></view>
        <view class="stat"><text>本人待缴</text><strong>¥{{ money(ledger.unpaidAmount) }}</strong></view>
        <view class="stat"><text>小区缴费率</text><strong>{{ money(ledger.paymentRate) }}%</strong></view>
      </view>
      <view v-for="flow in flows" :key="flow.id" class="card">
        <view><text class="title">{{ flow.summary }}</text><text class="muted">{{ flow.accountName }}</text></view>
        <view class="right"><strong :class="flow.direction === 'IN' ? 'income' : 'expense'">{{ flow.direction === 'IN' ? '+' : '-' }}¥{{ money(flow.amount) }}</strong><text class="muted">{{ flow.traceNo }}</text></view>
      </view>
      <view class="section-title">电子缴费票据</view>
      <view v-for="item in receipts" :key="item.id" class="card">
        <view><text class="title">{{ item.receiptNo }}</text><text class="muted">{{ item.billType }} · {{ item.period }} · {{ item.payChannel }}</text></view>
        <view class="right"><strong class="income">¥{{ money(item.amount) }}</strong><text class="muted">{{ item.status }} · {{ item.checksum }}</text></view>
      </view>
      <view class="section-title">欠费公开台账</view>
      <view v-for="item in arrearsPublicItems" :key="item.id" class="card">
        <view><text class="title">{{ item.roomNoMask }} · {{ item.billType }}</text><text class="muted">{{ item.period }} · 截止 {{ item.dueDate }}</text></view>
        <view class="right"><strong class="expense">¥{{ money(item.arrearsAmount) }}</strong><text class="muted">脱敏公示</text></view>
      </view>
    </view>

    <view v-if="tab === '公告'" class="list">
      <view v-for="item in messages" :key="item.id" class="card block notice">
        <text class="title">{{ item.title }}</text>
        <text class="muted">{{ item.channel }} · {{ item.status }} · {{ item.readStatus === 'READ' ? '已读' : '未读' }}</text>
        <text class="content">{{ item.content }}</text>
        <button v-if="item.readStatus !== 'READ'" class="inline-action" @click="markMessageRead(item)">标记已读</button>
      </view>
      <view v-for="item in announcements" :key="item.id" class="card block">
        <text class="title">{{ item.title }}</text>
        <text class="muted">{{ item.category }}</text>
        <text class="content">{{ item.content }}</text>
      </view>
    </view>

    <view v-if="tab === '投票'" class="list">
      <view v-for="item in votes" :key="item.id" class="card">
        <view><text class="title">{{ item.title }}</text><text class="muted">{{ item.voteType === 'SURVEY' ? '问卷' : '表决' }} · {{ item.status }} · 参与率 {{ item.participationRate }}%</text></view>
        <view v-if="item.voteType !== 'SURVEY'" class="right vote-actions"><strong>同意率 {{ item.agreeRate }}%</strong><button @click="castVote(item, 'AGREE')">同意</button><button class="ghost" @click="castVote(item, 'DISAGREE')">反对</button></view>
        <view v-else v-for="question in surveyQuestions[item.id] || []" :key="question.id" class="survey-box">
          <text class="content">{{ question.questionTitle }}</text>
          <view class="vote-actions">
            <button v-for="option in question.options" :key="option.id" @click="submitSurvey(item, question, option)">{{ option.optionLabel }}</button>
          </view>
        </view>
      </view>
    </view>

    <view v-if="tab === '报修'" class="list">
      <view class="card block form-card">
        <text class="title">提交报修/投诉</text>
        <view class="switch-row">
          <button :class="{ active: workOrderForm.orderType === 'REPAIR' }" @click="workOrderForm.orderType = 'REPAIR'">报修</button>
          <button :class="{ active: workOrderForm.orderType === 'COMPLAINT' }" @click="workOrderForm.orderType = 'COMPLAINT'">投诉</button>
        </view>
        <view class="switch-row">
          <button :class="{ active: workOrderForm.priority === 'NORMAL' }" @click="workOrderForm.priority = 'NORMAL'">普通</button>
          <button :class="{ active: workOrderForm.priority === 'URGENT' }" @click="workOrderForm.priority = 'URGENT'">紧急</button>
        </view>
        <input v-model="workOrderForm.title" class="field" placeholder="标题，例如：单元门禁无法刷卡" />
        <textarea v-model="workOrderForm.description" class="field textarea" placeholder="请描述位置、现象和影响范围" />
        <button class="submit" @click="submitWorkOrder">提交</button>
      </view>
      <view v-for="item in workOrders" :key="item.id" class="card block">
        <text class="title">{{ item.title }}</text>
        <text class="muted">{{ item.orderType === 'COMPLAINT' ? '投诉' : '报修' }} · {{ item.priority === 'URGENT' ? '紧急' : '普通' }} · {{ item.status }} · {{ item.roomNo }}</text>
        <text v-if="item.dueAt" class="muted">承诺截止：{{ item.dueAt }}</text>
        <text class="content">{{ item.description }}</text>
        <text v-if="item.reply" class="reply">处理回复：{{ item.reply }}</text>
        <text v-if="item.satisfactionScore" class="reply">我的评价：{{ item.satisfactionScore }} 星 {{ item.satisfactionComment || '' }}</text>
        <view v-else-if="['DONE', 'CLOSED'].includes(item.status)" class="rating-row">
          <button v-for="score in [5, 4, 3]" :key="score" @click="evaluateWorkOrder(item, score)">{{ score }}星</button>
        </view>
      </view>
    </view>

    <view v-if="tab === '我的'" class="list">
      <view class="card block form-card">
        <text class="title">账号注册/登录</text>
        <input v-model="loginForm.username" class="field" placeholder="登录账号" />
        <input v-model="loginForm.password" class="field" placeholder="登录密码" />
        <input v-model="loginForm.name" class="field" placeholder="姓名" />
        <input v-model="loginForm.phone" class="field" placeholder="手机号" />
        <view class="switch-row">
          <button @click="login">登录</button>
          <button class="active" @click="registerOwner">注册</button>
        </view>
      </view>
      <view class="card block">
        <text class="title">实名房屋绑定</text>
        <text class="muted">{{ profile?.name }} · {{ profile?.communityName }} {{ profile?.building }} {{ profile?.roomNo }}</text>
        <text class="content">身份证：{{ profile?.identityMask || '未绑定' }}</text>
      </view>
      <view v-for="item in houseBindings" :key="item.id" class="card block notice">
        <text class="title">{{ item.communityName }} {{ item.building }} {{ item.roomNo }}</text>
        <text class="muted">{{ item.residentType }} · {{ item.verificationStatus }}</text>
        <text class="content">{{ item.isPrimary ? '默认房屋' : '已绑定房屋' }}</text>
        <view class="switch-row">
          <button @click="switchHouse(item.houseId)">切换</button>
          <button class="active" @click="setDefaultHouse(item)">设为默认</button>
        </view>
      </view>
      <view v-for="item in houseBindingApplications" :key="item.applicationNo" class="card block">
        <text class="title">绑定申请 {{ item.applicationNo }}</text>
        <text class="muted">{{ item.status }} · {{ item.submittedAt }}</text>
        <text class="content">{{ item.reviewComment || '等待物业或平台审核' }}</text>
      </view>
      <view class="card block form-card">
        <text class="title">提交房屋绑定申请</text>
        <input v-model="bindForm.communityName" class="field" placeholder="小区名称" />
        <input v-model="bindForm.building" class="field" placeholder="楼栋" />
        <input v-model="bindForm.roomNo" class="field" placeholder="房号" />
        <input v-model="bindForm.name" class="field" placeholder="业主姓名" />
        <input v-model="bindForm.phone" class="field" placeholder="手机号" />
        <input v-model="bindForm.identityNo" class="field" placeholder="身份证号" />
        <button class="submit" @click="bindHouse">提交审核</button>
      </view>
    </view>
  </view>
</template>

<style>
.page { min-height: 100vh; padding: 28rpx; color: #17222b; font-family: "PingFang SC", "Microsoft YaHei", sans-serif; }
.page, .page view, .page text, .page button { box-sizing: border-box; }
.page { width: 100%; max-width: 100vw; overflow-x: hidden; padding-left: 18rpx; padding-right: 18rpx; }
.hero { width: 100%; display: block; padding: 28rpx; background: #10242b; color: #fff; border-radius: 16rpx; overflow: hidden; }
.hero > view:first-child { min-width: 0; }
.eyebrow { display: block; margin-bottom: 12rpx; color: #69d7cc; font-size: 22rpx; font-weight: 700; letter-spacing: 0; }
.hello { display: block; font-size: 36rpx; font-weight: 700; }
.home { display: block; margin-top: 12rpx; color: #b8ccd0; font-size: 24rpx; }
.amount { text-align: left; margin-top: 18rpx; }
.amount text { display: block; color: #b8ccd0; font-size: 24rpx; }
.amount strong { display: block; margin-top: 10rpx; font-size: 30rpx; white-space: nowrap; }
.tabs { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10rpx; margin: 24rpx 0; }
.tabs button { height: 68rpx; padding: 0; border: 0; border-radius: 10rpx; background: #fff; color: #586a72; font-size: 26rpx; line-height: 68rpx; }
.tabs button.active { background: #158f84; color: #fff; }
.list { display: grid; gap: 18rpx; }
.stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14rpx; }
.section-title { display: block; margin-top: 8rpx; color: #40525a; font-size: 26rpx; font-weight: 650; }
.stat { padding: 20rpx; border: 1px solid #dfe7ea; border-radius: 12rpx; background: #fff; }
.stat text { display: block; color: #71828a; font-size: 23rpx; }
.stat strong { display: block; margin-top: 10rpx; color: #17222b; font-size: 28rpx; white-space: nowrap; }
.card { width: 100%; display: block; padding: 24rpx; background: #fff; border: 1px solid #dfe7ea; border-radius: 14rpx; overflow: hidden; }
.card > view:first-child { min-width: 0; }
.card.block { display: grid; justify-content: stretch; }
.title { display: block; font-size: 30rpx; font-weight: 650; color: #17222b; }
.muted { display: block; margin-top: 8rpx; color: #71828a; font-size: 24rpx; }
.content { display: block; margin-top: 16rpx; color: #40525a; font-size: 26rpx; line-height: 1.6; }
.right { text-align: left; margin-top: 14rpx; }
.right strong { display: block; font-size: 27rpx; margin-bottom: 10rpx; white-space: nowrap; }
.right button { height: 56rpx; min-width: 104rpx; padding: 0 20rpx; border: 0; border-radius: 8rpx; background: #158f84; color: #fff; font-size: 24rpx; line-height: 56rpx; }
.pay-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10rpx; }
.pay-row button.alipay { background: #1677ff; }
.vote-actions button { margin-right: 12rpx; }
.vote-actions button.ghost { background: #eef3f4; color: #40525a; }
.survey-box { margin-top: 16rpx; }
.form-card { gap: 16rpx; }
.switch-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; }
.switch-row button { height: 58rpx; border: 0; border-radius: 8rpx; background: #eef3f4; color: #40525a; font-size: 24rpx; line-height: 58rpx; }
.switch-row button.active { background: #158f84; color: #fff; }
.rating-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; margin-top: 16rpx; }
.rating-row button { height: 58rpx; border: 0; border-radius: 8rpx; background: #eef3f4; color: #40525a; font-size: 24rpx; line-height: 58rpx; }
.inline-action { width: 180rpx; height: 56rpx; margin-top: 16rpx; border: 0; border-radius: 8rpx; background: #158f84; color: #fff; font-size: 24rpx; line-height: 56rpx; }
.field { width: 100%; height: 70rpx; padding: 0 18rpx; border: 1px solid #dfe7ea; border-radius: 10rpx; background: #fbfcfc; color: #17222b; font-size: 26rpx; }
.textarea { height: 150rpx; padding-top: 16rpx; line-height: 1.5; }
.submit { width: 100%; height: 64rpx; border: 0; border-radius: 8rpx; background: #158f84; color: #fff; font-size: 26rpx; line-height: 64rpx; }
.reply { display: block; margin-top: 16rpx; padding: 16rpx; border-radius: 10rpx; background: #eef6f5; color: #1f5f58; font-size: 25rpx; line-height: 1.5; }
.notice { border-color: #bfdedb; background: #f7fbfa; }
.paid { color: #158f84; font-size: 24rpx; }
.income { color: #158f84; }
.expense { color: #c87512; }
</style>
