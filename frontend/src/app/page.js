import Link from 'next/link'
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  BarChart3,
  Bell,
  Bookmark,
  Database,
  Layers,
  Radio,
  ShieldCheck,
} from 'lucide-react'
import LaunchLink from './LaunchLink'
import styles from './landing.module.css'

const pipeline = [
  { name: 'CAPTURE', description: 'Low-latency tick ingestion' },
  { name: 'STREAM', description: 'WebSocket buffer & Kafka' },
  { name: 'ANALYZE', description: 'RSI, SMA, EMA, Momentum' },
  { name: 'INTERPRET', description: '4-Factor conviction scoring' },
  { name: 'VISUALIZE', description: 'Real-time terminal output' },
]

const features = [
  {
    icon: Radio,
    number: '01',
    title: 'Real-Time Market Streaming',
    description: 'A responsive stream pipeline built to move market ticks from ingestion to the screen with bounded concurrency.',
  },
  {
    icon: BarChart3,
    number: '02',
    title: 'Multi-Signal Analysis',
    description: 'Technical context from trend, RSI, momentum, and relative volume, calculated as the stream advances.',
  },
  {
    icon: Activity,
    number: '03',
    title: 'Conviction Scoring',
    description: 'An explainable 4-factor score that brings multiple indicators into one decision-support view.',
  },
  {
    icon: Bell,
    number: '04',
    title: 'Alerts & Watchlists',
    description: 'Keep tracked instruments and threshold alerts close to the same live analytical workflow.',
  },
]

const scoreInputs = ['Trend', 'RSI', 'Momentum', 'Relative volume']

export default function LandingPage() {
  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div className={`${styles.container} ${styles.navbar}`}>
          <Link className={styles.brand} href="/" aria-label="QuantStream home">
            <span className={styles.brandMark} aria-hidden="true"><Activity size={20} /></span>
            <span className={styles.brandName}>QUANT<span>STREAM</span></span>
          </Link>

          <nav className={styles.navLinks} aria-label="Main navigation">
            <a href="#pipeline">Pipeline</a>
            <a href="#conviction">Conviction</a>
            <a href="#platform">Platform</a>
          </nav>

          <div className={styles.navActions}>
            <Link className={styles.navSignIn} href="/sign-in">Sign In</Link>
            <Link className={styles.navGetStarted} href="/sign-up">
              Get Started <ArrowUpRight size={15} aria-hidden="true" />
            </Link>
          </div>
        </div>
      </header>

      <section className={`${styles.container} ${styles.hero}`}>
        <div className={styles.heroCopy}>
          <p className={styles.eyebrow}><span /> MARKET INTELLIGENCE, IN MOTION</p>
          <h1>FROM DATA<br /><span>TO DECISION.</span></h1>
          <p className={styles.heroLead}>
            Real-time market intelligence that transforms streaming market data into actionable conviction.
          </p>
          <div className={styles.heroActions}>
            <Link className={styles.primaryButton} href="/sign-up">
              Get Started <ArrowRight size={17} aria-hidden="true" />
            </Link>
            <Link className={styles.secondaryButton} href="/sign-in">Sign In</Link>
          </div>
          <div className={styles.heroMeta}>
            <span><i className={styles.metaDot} /> STREAM-READY ARCHITECTURE</span>
            <span className={styles.metaDivider} />
            <span>BUILT FOR CLARITY UNDER LOAD</span>
          </div>
        </div>

        <div className={styles.heroVisual} aria-label="Illustrative QuantStream signal processing diagram">
          <div className={styles.visualHeader}>
            <span>QS / SIGNAL ARCHITECTURE</span>
            <span className={styles.visualIndex}>FIG. 01</span>
          </div>
          <div className={styles.flowDiagram}>
            <div className={styles.flowLine} aria-hidden="true" />
            <div className={styles.flowNode}>
              <span className={styles.nodeIcon}><Database size={19} /></span>
              <span className={styles.nodeTitle}>MARKET DATA</span>
              <span className={styles.nodeNote}>STREAM INPUT</span>
            </div>
            <div className={`${styles.flowNode} ${styles.flowNodeActive}`}>
              <span className={styles.nodeIcon}><Layers size={19} /></span>
              <span className={styles.nodeTitle}>SIGNAL ENGINE</span>
              <span className={styles.nodeNote}>4-FACTOR MODEL</span>
            </div>
            <div className={styles.flowNode}>
              <span className={styles.nodeIcon}><ShieldCheck size={19} /></span>
              <span className={styles.nodeTitle}>DECISION VIEW</span>
              <span className={styles.nodeNote}>CLEAR CONTEXT</span>
            </div>
          </div>
          <div className={styles.visualFooter}>
            <span>INGEST</span><span>PROCESS</span><span>INTERPRET</span>
          </div>
          <div className={styles.visualStamp}>SYSTEM OVERVIEW <b>ILLUSTRATIVE</b></div>
        </div>
      </section>

      <section className={styles.pipelineSection} id="pipeline">
        <div className={styles.container}>
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.sectionKicker}>01 / THE PIPELINE</p>
              <h2>From tick to <span>context.</span></h2>
            </div>
            <p className={styles.sectionIntro}>A clear path from market events to decision-ready market intelligence.</p>
          </div>
          <div className={styles.pipeline}>
            {pipeline.map((step, index) => (
              <div className={styles.pipelineStep} key={step.name}>
                <span className={styles.pipelineNumber}>0{index + 1}</span>
                <span className={styles.pipelineName}>{step.name}</span>
                <span className={styles.pipelineDescription}>{step.description}</span>
                {index < pipeline.length - 1 && <ArrowRight className={styles.pipelineArrow} size={16} aria-hidden="true" />}
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className={`${styles.container} ${styles.convictionSection}`} id="conviction">
        <div className={styles.convictionCopy}>
          <p className={styles.sectionKicker}>02 / THE CONVICTION SCORE</p>
          <h2>One score.<br /><span>Multiple signals.</span><br />Clearer context.</h2>
          <p>
            QuantStream brings trend, momentum, relative volume, and RSI into an explainable scoring model. The result is a consistent analytical reference, not a substitute for judgment.
          </p>
          <div className={styles.modelNote}><ShieldCheck size={17} /> DECISION SUPPORT, NOT TRADE EXECUTION</div>
        </div>
        <div className={styles.scoreGraphic}>
          <div className={styles.scoreGraphicHeader}>
            <span>MODEL COMPOSITION</span>
            <span>ILLUSTRATIVE · NO LIVE VALUES</span>
          </div>
          <div className={styles.scoreDiagram}>
            <div className={styles.inputStack}>
              {scoreInputs.map((input, index) => (
                <div className={styles.scoreInput} key={input}>
                  <span className={`${styles.inputMarker} ${index === 1 ? styles.markerAmber : ''}`} />
                  <span>{input}</span>
                  <small>INPUT</small>
                </div>
              ))}
            </div>
            <div className={styles.scoreConnector} aria-hidden="true"><span /><span /><span /><span /></div>
            <div className={styles.scoreOutput}>
              <span className={styles.outputLabel}>CONVICTION</span>
              <strong>0–100</strong>
              <span className={styles.outputCaption}>CONTEXTUAL RANGE</span>
            </div>
          </div>
          <div className={styles.scoreGraphicFooter}>
            <span>4 SIGNAL FAMILIES</span><span>ONE EXPLAINABLE VIEW</span>
          </div>
        </div>
      </section>

      <section className={styles.featureSection} id="platform">
        <div className={styles.container}>
          <div className={styles.sectionHeading}>
            <div>
              <p className={styles.sectionKicker}>03 / THE PLATFORM</p>
              <h2>Built around the <span>signal.</span></h2>
            </div>
            <p className={styles.sectionIntro}>A focused toolset for following the stream, understanding the model, and acting with context.</p>
          </div>
          <div className={styles.featureGrid}>
            {features.map(({ icon: Icon, number, title, description }) => (
              <article className={styles.feature} key={number}>
                <div className={styles.featureTop}>
                  <span className={styles.featureIcon}><Icon size={19} strokeWidth={1.7} /></span>
                  <span className={styles.featureNumber}>{number}</span>
                </div>
                <h3>{title}</h3>
                <p>{description}</p>
                <span className={styles.featureRule} />
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className={`${styles.container} ${styles.previewSection}`}>
        <div className={styles.sectionHeading}>
          <div>
            <p className={styles.sectionKicker}>04 / THE WORKSPACE</p>
            <h2>Built for the <span>whole picture.</span></h2>
          </div>
          <p className={styles.sectionIntro}>One terminal for market overview, instrument analysis, alerts, and engine health.</p>
        </div>

        <div className={styles.previewShell}>
          <div className={styles.previewTopbar}>
            <div className={styles.previewBrand}><span><Activity size={15} /></span> QUANTSTREAM <i>/</i> MARKET OVERVIEW</div>
            <span className={styles.previewBadge}>STATIC PRODUCT PREVIEW · NOT LIVE</span>
          </div>
          <div className={styles.previewBody}>
            <aside className={styles.previewRail} aria-label="Preview navigation">
              <span className={styles.railActive}><Activity size={16} /></span>
              <span><BarChart3 size={16} /></span>
              <span><Bookmark size={16} /></span>
              <span><Bell size={16} /></span>
            </aside>
            <div className={styles.previewMain}>
              <div className={styles.previewTitleRow}>
                <div><span className={styles.previewOverline}>WORKSPACE / 01</span><h3>Market overview</h3></div>
                <span className={styles.previewOffline}><i /> NO LIVE FEED</span>
              </div>
              <div className={styles.previewMetrics}>
                {['MARKET BREADTH', 'AVG CONVICTION', 'STREAM STATUS'].map((label) => (
                  <div className={styles.previewMetric} key={label}>
                    <span>{label}</span><strong>—</strong><small>AWAITING DATA</small>
                  </div>
                ))}
              </div>
              <div className={styles.previewLower}>
                <div className={styles.previewChart}>
                  <div className={styles.previewChartHeader}><span>INSTRUMENT STREAM</span><span>PREVIEW</span></div>
                  <div className={styles.chartEmpty}><span className={styles.chartCrosshair}>+</span><span>Market data appears here when connected</span></div>
                  <div className={styles.chartAxis}><span>PRICE</span><span>TIME →</span></div>
                </div>
                <div className={styles.previewSignals}>
                  <span className={styles.previewChartHeader}>SIGNAL CONTEXT</span>
                  {['TREND', 'RSI', 'MOMENTUM', 'VOLUME'].map((label) => (
                    <div className={styles.previewSignalRow} key={label}><span>{label}</span><i /> <small>—</small></div>
                  ))}
                  <p>No market values shown in this preview.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <p className={styles.previewCaption}><span /> PRODUCT PREVIEW · DATA VALUES INTENTIONALLY OMITTED</p>
      </section>

      <section className={styles.finalSection}>
        <div className={`${styles.container} ${styles.finalInner}`}>
          <div>
            <p className={styles.sectionKicker}>QUANTSTREAM / READY WHEN YOU ARE</p>
            <h2>Turn market data into a<br /><span>decision-ready signal.</span></h2>
          </div>
          <LaunchLink />
        </div>
      </section>

      <footer className={`${styles.container} ${styles.footer}`}>
        <Link className={styles.brand} href="/">
          <span className={styles.brandMark} aria-hidden="true"><Activity size={17} /></span>
          <span className={styles.brandName}>QUANT<span>STREAM</span></span>
        </Link>
        <p>REAL-TIME MARKET INTELLIGENCE</p>
        <span className={styles.footerLegal}>ANALYTICS FOR DECISION SUPPORT</span>
      </footer>
    </main>
  )
}