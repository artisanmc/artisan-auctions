// E2E of Artisan Auctions: node run.js <port> <consoleFile>
const mineflayer = require('mineflayer')
const fs = require('fs')
const [port, consoleFile] = [Number(process.argv[2]), process.argv[3]]
const sleep = ms => new Promise(r => setTimeout(r, ms))
const results = []
const check = (name, ok, detail = '') => { results.push({ name, ok, detail }); console.log(`${ok ? 'PASS' : 'FAIL'} ${name} ${detail}`) }
const consoleCmd = c => fs.appendFileSync(consoleFile, c + '\n')

function join(name) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version: '1.21.8', auth: 'offline' })
    bot.log = []
    bot.on('messagestr', m => { bot.log.push(m); if (process.env.VERBOSE) console.log(`[${name}] ${m}`) })
    bot.once('spawn', () => resolve(bot))
    bot.on('kicked', r => console.log(`[${name}] kicked`, JSON.stringify(r)))
    bot.on('error', e => { console.log(`[${name}] error`, e.message); reject(e) })
    bot.on('resourcePack', () => { try { bot.acceptResourcePack() } catch {} })
  })
}

// Runs a command and waits for a chat line matching re (returns it) or times out (null).
async function ask(bot, cmd, re, ms = 6000) {
  const start = bot.log.length
  bot.chat(cmd)
  const end = Date.now() + ms
  while (Date.now() < end) {
    const hit = bot.log.slice(start).find(l => re.test(l))
    if (hit) return hit
    await sleep(100)
  }
  return null
}
async function lines(bot, cmd, ms = 2500) { const s = bot.log.length; bot.chat(cmd); await sleep(ms); return bot.log.slice(s) }
async function money(bot, who) { const l = await ask(bot, `/e2e money ${who}`, new RegExp(`^E2E money ${who} `)); return l && l.split(' ').pop() }
async function count(bot, who, mat) { const l = await ask(bot, `/e2e count ${who} ${mat}`, new RegExp(`^E2E count ${who} ${mat} `)); return l && Number(l.split(' ').pop()) }
async function listings(bot) { const out = await lines(bot, '/e2e listings'); return out.filter(l => l.startsWith('E2E listing ')).map(l => l.split(' ')) }

;(async () => {
  for (const n of ['Seller', 'Buyer', 'Bidder']) consoleCmd(`op ${n}`)
  await sleep(1000)
  const seller = await join('Seller'); await sleep(1500)
  const buyer = await join('Buyer'); await sleep(1500)
  const bidder = await join('Bidder'); await sleep(3000)
  for (const b of [seller, buyer, bidder]) { b.chat('/gamemode survival'); b.chat('/clear'); await sleep(300) }

  // 1. Fixed-price sale, buyer online.
  await ask(seller, '/e2e setmoney Seller 0', /^E2E money/)
  await ask(seller, '/e2e setmoney Buyer 150', /^E2E money/)
  await ask(seller, '/e2e setmoney Bidder 500', /^E2E money/)
  seller.chat('/clear'); await sleep(300)
  seller.chat('/give Seller diamond 10'); await sleep(800)
  seller.setQuickBarSlot(0); await sleep(300)
  let out = await lines(seller, '/ah sell 100', 3500)
  check('sell lists the held item', (await count(seller, 'Seller', 'DIAMOND')) === 0, JSON.stringify(out))
  check('texts are translated', out.length > 0 && !out.some(l => l.includes('{i18n:')), JSON.stringify(out))
  let ls = await listings(seller)
  check('one active listing', ls.length === 1, JSON.stringify(ls))
  const saleId = ls[0] && ls[0][2]
  out = await lines(buyer, `/e2e act auctions:buy ${saleId}`, 3500)
  check('buyer gets the diamonds', (await count(buyer, 'Buyer', 'DIAMOND')) === 10, JSON.stringify(out))
  check('buyer paid 100', (await money(buyer, 'Buyer')) === '50')
  check('seller got 95 (5 % tax)', (await money(seller, 'Seller')) === '95')
  check('no listing left', (await listings(seller)).length === 0)

  // 2. Double buy of one listing: one wins.
  seller.chat('/give Seller emerald 3'); await sleep(800)
  seller.setQuickBarSlot(0); await sleep(200)
  await lines(seller, '/ah sell 10', 3000)
  ls = await listings(seller)
  const raceId = ls[0] && ls[0][2]
  await ask(seller, '/e2e setmoney Bidder 500', /^E2E money/)
  buyer.chat(`/e2e act auctions:buy ${raceId}`); bidder.chat(`/e2e act auctions:buy ${raceId}`)
  await sleep(4000)
  const em = (await count(buyer, 'Buyer', 'EMERALD')) + (await count(bidder, 'Bidder', 'EMERALD'))
  const spent = (50 - Number(await money(buyer, 'Buyer'))) + (500 - Number(await money(bidder, 'Bidder')))
  check('a raced listing is sold once', em === 3 && spent === 10, `emeralds=${em} spent=${spent}`)

  // 3. Auction: bid, outbid (refund), admin cancel (refund + items back).
  await ask(seller, '/e2e setmoney Buyer 300', /^E2E money/)
  await ask(seller, '/e2e setmoney Bidder 500', /^E2E money/)
  seller.chat('/give Seller gold_ingot 5'); await sleep(800)
  seller.setQuickBarSlot(0); await sleep(200)
  out = await lines(seller, '/ah auction 100 1h', 3500)
  ls = await listings(seller)
  const aucId = ls[0] && ls[0][2]
  check('auction listed', ls.length === 1 && ls[0][3] === 'AUCTION', JSON.stringify(out))
  await lines(bidder, `/e2e act auctions:bid ${aucId} 100`, 3000)
  check('bid takes the money', (await money(bidder, 'Bidder')) === '400')
  await lines(buyer, `/e2e act auctions:bid ${aucId} 120`, 3000)
  check('outbid bidder refunded', (await money(bidder, 'Bidder')) === '500')
  check('new bid taken', (await money(buyer, 'Buyer')) === '180')
  out = await lines(seller, `/ah admin cancel ${aucId.slice(0, 8)}`, 3000)
  check('cancel refunds the leading bid', (await money(buyer, 'Buyer')) === '300', JSON.stringify(out))
  // Items go to the claims box of the seller: claim them.
  out = await lines(seller, '/ah claim', 1500)
  if (seller.currentWindow) seller.closeWindow(seller.currentWindow)
  await lines(seller, '/e2e act auctions:claim all', 3000)
  check('cancelled items claimed back', (await count(seller, 'Seller', 'GOLD_INGOT')) === 5)

  // 4. Offline seller is paid on join.
  seller.chat('/give Seller iron_ingot 4'); await sleep(800)
  seller.setQuickBarSlot(0); await sleep(200)
  await lines(seller, '/ah sell 40', 3000)
  ls = await listings(seller)
  const offId = ls[0] && ls[0][2]
  const before = Number(await money(seller, 'Seller'))
  seller.quit(); await sleep(2000)
  await lines(buyer, `/e2e act auctions:buy ${offId}`, 3500)
  // The currency pays offline players: the seller is paid at once, nothing waits in the box.
  check('offline seller paid at once', Number(await money(buyer, 'Seller')) === before + 38, `before=${before}`)
  const seller2 = await join('Seller'); await sleep(4000)
  check('nothing paid twice on join', Number(await money(seller2, 'Seller')) === before + 38)

  // 5. The main menu opens and shows its grid.
  const win = new Promise(r => seller2.once('windowOpen', w => r(w)))
  seller2.chat('/ah')
  const w = await Promise.race([win, sleep(5000).then(() => null)])
  check('/ah opens a menu', !!w, w ? `${w.type} ${JSON.stringify(w.title)} slots=${w.slots.filter(Boolean).length}` : '')

  const failed = results.filter(r => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  for (const b of [seller2, buyer, bidder]) b.quit()
  await sleep(500)
  process.exit(failed.length ? 1 : 0)
})().catch(e => { console.error(e); process.exit(2) })
