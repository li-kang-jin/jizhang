<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import { fetchOverview, fetchTrend } from '@/api/admin'
import { formatAmount } from '@/types'
import type { PlatformOverview, TrendPoint } from '@/types'

const loading = ref(false)
const overview = ref<PlatformOverview | null>(null)
const trend = ref<TrendPoint[]>([])
const days = ref(30)

const chartRef = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()

async function loadData() {
  loading.value = true
  try {
    const [overviewData, trendData] = await Promise.all([fetchOverview(), fetchTrend(days.value)])
    overview.value = overviewData
    trend.value = trendData
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) {
    return
  }
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value)
  }
  chart.value.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增用户', '交易笔数'] },
    grid: { left: 48, right: 36, top: 54, bottom: 36 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.map((item) => item.date.slice(5))
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
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
  })
}

function handleResize() {
  chart.value?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart.value?.dispose()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="cards">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-label">用户总数</div>
        <div class="stat-value">{{ overview?.userTotal ?? 0 }}</div>
        <div class="stat-extra">今日新增 {{ overview?.userToday ?? 0 }}</div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-label">账本总数</div>
        <div class="stat-value">{{ overview?.bookTotal ?? 0 }}</div>
        <div class="stat-extra">全平台累计</div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-label">交易总笔数</div>
        <div class="stat-value">{{ overview?.transactionTotal ?? 0 }}</div>
        <div class="stat-extra">今日新增 {{ overview?.transactionToday ?? 0 }}</div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-label">累计交易金额（元）</div>
        <div class="stat-value amount">{{ formatAmount(overview?.amountTotal) }}</div>
        <div class="stat-extra">今日 {{ formatAmount(overview?.amountToday) }}</div>
      </el-card>
    </div>

    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="chart-header">
          <span>平台增长趋势</span>
          <el-radio-group v-model="days" size="small" @change="loadData">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
            <el-radio-button :value="90">近 90 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-value.amount {
  color: #f56c6c;
  font-size: 23px;
}

.chart-card {
  margin-top: 16px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.chart {
  height: 380px;
}

@media (max-width: 1200px) {
  .cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
