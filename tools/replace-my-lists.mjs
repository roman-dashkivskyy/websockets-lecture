#!/usr/bin/env node
// Demo helper for phase 5: replace ALL of a user's lists with a big generated set.
//
//   node tools/replace-my-lists.mjs <user> <lists> <items-per-list> [base-url]
//   node tools/replace-my-lists.mjs carol 50 200   (not alice: that would delete the seeded demo lists)
//
// Sends one PUT /api/lists/mine through the load balancer (http://localhost:8080 by default) and prints how big
// the HTTP request was and how long it took. The broker message it causes stays tiny: just ids.
// Needs Node 22+ (built-in fetch), no dependencies. Demo password: demo1234.

const [user, listsArg, itemsArg, baseUrl = 'http://localhost:8080'] = process.argv.slice(2)
const lists = Number(listsArg)
const itemsPerList = Number(itemsArg)

if (!user || !Number.isInteger(lists) || !Number.isInteger(itemsPerList) || lists < 0 || itemsPerList < 0) {
  console.error('Usage: node tools/replace-my-lists.mjs <user> <lists> <items-per-list> [base-url]')
  process.exit(1)
}

async function main() {
  const login = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: 'demo1234' }),
  })
  if (!login.ok) throw new Error(`Login failed: ${login.status}`)
  const { token } = await login.json()

  const payload = Array.from({ length: lists }, (_, l) => ({
    title: `Generated list ${l + 1}`,
    visibility: l % 2 === 0 ? 'PUBLIC' : 'PRIVATE',
    items: Array.from({ length: itemsPerList }, (_, i) => ({ title: `Task ${l + 1}.${i + 1}`, done: i % 3 === 0 })),
  }))
  const body = JSON.stringify(payload)

  const started = performance.now()
  const response = await fetch(`${baseUrl}/api/lists/mine`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body,
  })
  const elapsed = performance.now() - started
  if (!response.ok) throw new Error(`Replace failed: ${response.status} ${await response.text()}`)

  console.log(`Replaced ${user}'s lists with ${lists} lists x ${itemsPerList} items (${lists * itemsPerList} items)`)
  console.log(`HTTP request body: ${(Buffer.byteLength(body) / 1024).toFixed(1)} KB, took ${elapsed.toFixed(0)} ms`)
  console.log(`Answered by instance: ${response.headers.get('X-Instance-Name')}`)
}

main().catch((error) => {
  console.error(error.message, error.cause?.message ?? '') // fetch hides e.g. ECONNREFUSED in error.cause
  process.exit(1)
})
