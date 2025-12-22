/**
 * TTS 语音播放工具
 * 用于播放 Agent 回复的语音
 */

export class TtsPlayer {
  private audioContext: AudioContext | null = null
  private currentSource: AudioBufferSourceNode | null = null
  private isPlaying = false
  private onPlayStateChange: ((playing: boolean) => void) | null = null

  constructor() {
    // 延迟初始化 AudioContext（需要用户交互后才能创建）
  }

  /**
   * 初始化 AudioContext
   */
  private initAudioContext() {
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
    }
    // 如果被暂停，恢复
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume()
    }
  }

  /**
   * 设置播放状态变化回调
   */
  setOnPlayStateChange(callback: (playing: boolean) => void) {
    this.onPlayStateChange = callback
  }

  /**
   * 播放音频 Blob
   */
  async play(audioBlob: Blob): Promise<void> {
    this.initAudioContext()
    
    if (!this.audioContext) {
      throw new Error('AudioContext not available')
    }

    // 停止当前播放
    this.stop()

    try {
      const arrayBuffer = await audioBlob.arrayBuffer()
      const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer)

      this.currentSource = this.audioContext.createBufferSource()
      this.currentSource.buffer = audioBuffer
      this.currentSource.connect(this.audioContext.destination)

      this.currentSource.onended = () => {
        this.isPlaying = false
        this.currentSource = null
        this.onPlayStateChange?.(false)
      }

      this.currentSource.start(0)
      this.isPlaying = true
      this.onPlayStateChange?.(true)
    } catch (error) {
      console.error('[TtsPlayer] 播放失败:', error)
      this.isPlaying = false
      this.onPlayStateChange?.(false)
      throw error
    }
  }

  /**
   * 停止播放
   */
  stop() {
    if (this.currentSource) {
      try {
        this.currentSource.stop()
      } catch (e) {
        // 忽略已停止的错误
      }
      this.currentSource = null
    }
    this.isPlaying = false
    this.onPlayStateChange?.(false)
  }

  /**
   * 是否正在播放
   */
  getIsPlaying(): boolean {
    return this.isPlaying
  }

  /**
   * 销毁资源
   */
  destroy() {
    this.stop()
    if (this.audioContext) {
      this.audioContext.close()
      this.audioContext = null
    }
  }
}

// 单例实例
let ttsPlayerInstance: TtsPlayer | null = null

export function getTtsPlayer(): TtsPlayer {
  if (!ttsPlayerInstance) {
    ttsPlayerInstance = new TtsPlayer()
  }
  return ttsPlayerInstance
}
