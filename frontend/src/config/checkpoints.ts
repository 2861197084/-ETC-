/**
 * 徐州市出市卡口数据（19个）
 * 数据来源：交通管理部门
 * 
 * 类型说明：
 * - provincial: 省际卡口（苏皖界/苏鲁界，出省）
 * - municipal: 市际卡口（出市不出省）
 */

export interface Checkpoint {
  id: string
  code: string
  name: string
  fullName: string
  longitude: number
  latitude: number
  region: string
  type: 'provincial' | 'municipal'
  road: string
  boundary: string // 交界方向：苏皖界/苏鲁界/连云港界/宿迁界等
  status: 'online' | 'offline' | 'maintenance'
}

export const checkpoints: Checkpoint[] = [
  {
    id: 'CP001',
    code: 'G104-AH-SN',
    name: 'G104苏皖界(睢宁)',
    fullName: '徐州市睢宁县G104北京-福州K873江苏徐州-G104-苏皖界省际卡口',
    longitude: 117.8682,
    latitude: 33.8095,
    region: '睢宁县',
    type: 'provincial',
    road: 'G104',
    boundary: '苏皖界',
    status: 'online'
  },
  {
    id: 'CP002',
    code: 'G310-AH-TS',
    name: 'G310苏皖界(铜山)',
    fullName: '徐州市铜山县G310连云港-天水K310江苏徐州-G310-苏皖界省际卡口',
    longitude: 117.1965,
    latitude: 34.1482,
    region: '铜山区',
    type: 'provincial',
    road: 'G310',
    boundary: '苏皖界',
    status: 'online'
  },
  {
    id: 'CP003',
    code: 'S323-LYG-XY',
    name: 'S323阿湖卡口(新沂)',
    fullName: '徐州市新沂市s323连徐线K10阿湖卡口-323省道连云港交界市际卡口',
    longitude: 118.6048,
    latitude: 34.3742,
    region: '新沂市',
    type: 'municipal',
    road: 'S323',
    boundary: '连云港界',
    status: 'online'
  },
  {
    id: 'CP004',
    code: 'G311-AH-TS',
    name: 'G311苏皖界(铜山)',
    fullName: '徐州市铜山县G311徐州-西峡K207江苏徐州-G311-苏皖界省际卡口',
    longitude: 117.2981,
    latitude: 34.1976,
    region: '铜山区',
    type: 'provincial',
    road: 'G311',
    boundary: '苏皖界',
    status: 'online'
  },
  {
    id: 'CP005',
    code: 'S253-SD-PX',
    name: 'S253苏鲁界(沛县)',
    fullName: '徐州市沛县s253郑沛龙线K0江苏徐州-s253-苏鲁界省际卡口',
    longitude: 116.9274,
    latitude: 34.7285,
    region: '沛县',
    type: 'provincial',
    road: 'S253',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP006',
    code: 'S323-WY-XY',
    name: 'S323瓦窑检查站(新沂)',
    fullName: '江苏省徐州市新沂市s323连徐线K96瓦窑检查站市际卡口',
    longitude: 118.2387,
    latitude: 34.3791,
    region: '新沂市',
    type: 'municipal',
    road: 'S323',
    boundary: '市际',
    status: 'online'
  },
  {
    id: 'CP007',
    code: 'S250-SD-PZ',
    name: 'S250苏鲁界(邳州)',
    fullName: '徐州市邳州市s250宿邳线K1江苏徐州-s250-苏鲁界省际卡口',
    longitude: 117.8972,
    latitude: 34.2985,
    region: '邳州市',
    type: 'provincial',
    road: 'S250',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP008',
    code: 'S505-SQ-XY',
    name: 'S505宿迁界(新沂)',
    fullName: '徐州市新沂市s505省道K10新沂高速西出口-505省道宿迁界市际卡口',
    longitude: 118.2963,
    latitude: 34.3478,
    region: '新沂市',
    type: 'municipal',
    road: 'S505',
    boundary: '宿迁界',
    status: 'online'
  },
  {
    id: 'CP009',
    code: 'S324-SZ-SN',
    name: 'S324桑庄卡口(睢宁)',
    fullName: '徐州市睢宁县S324燕沭睢线K201省道桑庄王马路路口西侧-向东卡口市际卡口',
    longitude: 117.8951,
    latitude: 33.9974,
    region: '睢宁县',
    type: 'municipal',
    road: 'S324',
    boundary: '市际',
    status: 'online'
  },
  {
    id: 'CP010',
    code: 'G518-SD-FX',
    name: 'G518马楼公路站(丰县)',
    fullName: '徐州市丰县G518国道K358马楼公路站省际卡口',
    longitude: 116.7583,
    latitude: 34.5692,
    region: '丰县',
    type: 'provincial',
    road: 'G518',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP011',
    code: 'G237-SD-FX',
    name: 'G237荣庄卡口(丰县)',
    fullName: '徐州市丰县G237国道237线K148荣庄卡口省际卡口',
    longitude: 116.5976,
    latitude: 34.6981,
    region: '丰县',
    type: 'provincial',
    road: 'G237',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP012',
    code: 'G235-MJ-XY',
    name: 'G235交界卡口(新沂)',
    fullName: '徐州市新沂市G235国道235K10江苏徐州-G235-交界市际卡口',
    longitude: 118.3482,
    latitude: 34.3795,
    region: '新沂市',
    type: 'municipal',
    road: 'G235',
    boundary: '市际',
    status: 'online'
  },
  {
    id: 'CP013',
    code: 'S251-SD-PZ',
    name: 'S251苏鲁界(邳州)',
    fullName: '徐州市邳州市s251枣睢线K5江苏徐州-s251-苏鲁界省际卡口',
    longitude: 117.9683,
    latitude: 34.3192,
    region: '邳州市',
    type: 'provincial',
    road: 'S251',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP014',
    code: 'LLR-LZ-FX',
    name: '梁寨检查站(丰县)',
    fullName: '徐州市丰县鹿梁路K19丰县梁寨检查站市际卡口',
    longitude: 116.6475,
    latitude: 34.7483,
    region: '丰县',
    type: 'municipal',
    road: '鹿梁路',
    boundary: '市际',
    status: 'online'
  },
  {
    id: 'CP015',
    code: 'G104-SD-TS',
    name: 'G104苏鲁界(铜山)',
    fullName: '徐州市铜山县G104北京-福州K744江苏徐州-G104-苏鲁界省际卡口',
    longitude: 117.0973,
    latitude: 34.2481,
    region: '铜山区',
    type: 'provincial',
    road: 'G104',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP016',
    code: 'G3-SD-XZ',
    name: 'G3京台高速苏鲁界',
    fullName: 'G3京台高速K731江苏高速五大队江苏徐州-G3-苏鲁界省际卡口',
    longitude: 117.4982,
    latitude: 34.7975,
    region: '徐州市',
    type: 'provincial',
    road: 'G3',
    boundary: '苏鲁界',
    status: 'online'
  },
  {
    id: 'CP017',
    code: 'S325-HS-SN',
    name: 'S325淮宿线卡口(睢宁)',
    fullName: '江苏省徐州市睢宁县s325淮宿线K63(325省道)63K+100M东侧-向西卡口市际卡口',
    longitude: 117.8982,
    latitude: 33.9965,
    region: '睢宁县',
    type: 'municipal',
    road: 'S325',
    boundary: '市际',
    status: 'online'
  },
  {
    id: 'CP018',
    code: 'S252-AH-SN',
    name: 'S252苏皖界(睢宁)',
    fullName: '徐州市睢宁县s252塔双线K56江苏徐州-s252-苏皖界省际卡口',
    longitude: 117.9473,
    latitude: 34.0482,
    region: '睢宁县',
    type: 'provincial',
    road: 'S252',
    boundary: '苏皖界',
    status: 'online'
  },
  {
    id: 'CP019',
    code: 'G206-AH-TS',
    name: 'G206苏皖界(铜山)',
    fullName: '徐州市铜山县G206烟台-汕头K816江苏徐州-G206-苏皖界省际卡口',
    longitude: 117.2481,
    latitude: 34.1973,
    region: '铜山区',
    type: 'provincial',
    road: 'G206',
    boundary: '苏皖界',
    status: 'online'
  }
]

// 按类型筛选
export const provincialCheckpoints = checkpoints.filter(cp => cp.type === 'provincial')
export const municipalCheckpoints = checkpoints.filter(cp => cp.type === 'municipal')

// 按区县分组
export const checkpointsByRegion = checkpoints.reduce((acc, cp) => {
  if (!acc[cp.region]) {
    acc[cp.region] = []
  }
  acc[cp.region].push(cp)
  return acc
}, {} as Record<string, Checkpoint[]>)

// 统计信息
export const checkpointStats = {
  total: checkpoints.length,
  provincial: provincialCheckpoints.length,  // 省际卡口数量
  municipal: municipalCheckpoints.length,    // 市际卡口数量
  regions: Object.keys(checkpointsByRegion)  // 涉及区县
}

// 地图中心点（徐州市中心）
export const mapCenter = {
  longitude: 117.2571,
  latitude: 34.2615
}

// 地图默认缩放级别
export const defaultZoom = 9
