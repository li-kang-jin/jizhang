<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import { fetchOverview, fetchTrend } from '@/api/admin'
import { formatAmount } from '@/types'
import type { PlatformOverview, TrendPoint } from '@/types'

const router = useRouter()

const loading = ref(false)
const error = ref(false)
const overview = ref<PlatformOverview | null>(null)
const trend = ref<TrendPoint[]>([])
const days = ref(30)
const metric = ref<'activity' | 'amount'>('activity')

const chartRef = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()
let observer: ResizeObserver | null = null

async function loadOverview() {
  overview.value = await fetchOverview()
}

async function loadTrend() {
  trend.value = await fetchTrend(days.value)
}

async function loadAll() {
  loading.value = true
  error.value = false
  try {
    await Promise.all([loadOverview(), loadTrend()])
    renderChart()
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

/** 切换天数只重拉趋势，总览和天数无关，没必要跟着打一次接口 */
async function handleDaysChange() {
  error.value = false
  try {
    await loadTrend()
    renderChart()
  } catch {
    error.value = true
  }
}

function renderChart() {
  if (!chartRef.value) {
    return
  }
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value)
  }

  const isAmount = metric.value === 'amount'
  const series = isAmount
    ? [
        {
          name: '支出',
          type: 'line',
          smooth: true,
          symbolSize: 5,
          areaStyle: { opacity: 0.1 },
          itemStyle: { color: '#f56c6c' },
          data: trend.value.map((item) => Number(((item.expense ?? 0) / 100).toFixed(2)))
        },
        {
          name: '收入',
          type: 'line',
          smooth: true,
          symbolSize: 5,
          areaStyle: { opacity: 0.1 },
          itemStyle: { color: '#67c23a' },
          data: trend.value.map((item) => Number(((item.income ?? 0) / 100).toFixed(2)))
        }
      ]
    : [
        {
          name: '新增用户',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          areaStyle: { opacity: 0.12 },
          itemStyle: { color: '#409eff' },
          data: trend.value.map((item) => item.newUsers)
        },
        {
          name: '交易笔数',
          type: 'line',
          smooth: true,
          symbolSize: 6,
          areaStyle: { opacity: 0.12 },
          itemStyle: { color: '#67c23a' },
          data: trend.value.map((item) => item.newTransactions)
        }
      ]

  chart.value.setOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: series.map((item) => item.name) },
      grid: { left: 56, right: 36, top: 54, bottom: 36 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.value.map((item) => item.date.slice(5))
      },
      yAxis: {
        type: 'value',
        minInterval: isAmount ? 0 : 1,
        name: isAmount ? '元' : ''
      },
      series
    },
    true
  )
}

watch(metric, () => renderChart())

function go(path: string, query?: Record<string, string>) {
  void router.push({ path, query })
}

onMounted(() => {
  loadAll()
  if (chartRef.value) {
    // 侧栏折叠、窗口缩放都不会触发 window.resize，用容器观察兜底
    observer = new ResizeObserver(() => chart.value?.resize())
    observer.observe(chartRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart.value?.dispose()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <el-alert v-if="error" type="error" :closable="false" show-icon class="load-error">
      <template #title>
        看板数据加载失败，当前显示的可能不是最新数据
        <el-button link type="primary" @click="loadAll">重新加载</el-button>
      </template>
    </el-alert>

    <div class="cards">
      <el-card shadow="hover" class="stat-card clickable" @click="go('/users')">
        <div class="stat-label">用户总数</div>
        <div class="stat-value">{{ overview?.userTotal ?? 0 }}</div>
        <div class="stat-extra">今日新增 {{ overview?.userToday ?? 0 }} 位</div>
      </el-card>

      <el-card shadow="hover" class="stat-card clickable" @click="go('/books')">
        <div class="stat-label">账本总数</div>
        <div class="stat-value">{{ overview?.bookTotal ?? 0 }}</div>
        <div class="stat-extra">今日活跃 {{ overview?.activeBookToday ?? 0 }} 个</div>
      </el-card>

      <el-card shadow="hover" class="stat-card clickable" @click="go('/transactions')">
        <div class="stat-label">交易笔数</div>
        <div class="stat-value">{{ overview?.transactionTotal ?? 0 }}</div>
        <div class="stat-extra">今日 {{ overview?.transactionToday ?? 0 }} 笔</div>
      </el-card>

      <el-card shadow="hover" class="stat-card clickable" @click="go('/transactions', { type: '1' })">
        <div class="stat-label">累计支出（元）</div>
        <div class="stat-value amount-expense">{{ formatAmount(overview?.expenseTotal) }}</div>
        <div class="stat-extra">今日 {{ formatAmount(overview?.expenseToday) }}</div>
      </el-card>

      <el-card shadow="hover" class="stat-card clickable" @click="go('/transactions', { type: '2' })">
        <div class="stat-label">累计收入（元）</div>
        <div class="stat-value amount-income">{{ formatAmount(overview?.incomeTotal) }}</div>
        <div class="stat-extra">今日 {{ formatAmount(overview?.incomeToday) }}</div>
      </el-card>
    </div>

    <p class="metric-note">金额口径：仅统计支出与收入，转账不计入，避免同一笔钱被重复累加。</p>

    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="chart-header">
          <span>平台趋势</span>
          <div class="chart-actions">
            <el-radio-group v-model="metric" size="small">
              <el-radio-button value="activity">活跃 / 笔数</el-radio-button>
              <el-radio-button value="amount">收支金额</el-radio-button>
            </el-radio-group>
            <el-radio-group v-model="days" size="small" @change="handleDaysChange">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
              <el-radio-button :value="90">近 90 天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<style scoped>
.load-error {
  margin-bottom: 16px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
}

.clickable {
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.clickable:hover {
  border-color: var(--el-color-primary-light-5);
}

.stat-value.amount-expense {
  color: #f56c6c;
  font-size: 23px;
}

.stat-value.amount-income {
  color: #67c23a;
  font-size: 23px;
}

.metric-note {
  margin: 12px 2px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.chart-card {
  margin-top: 16px;
}

.chart-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.chart-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.chart {
  height: 380px;
}
</style>
