import test from 'node:test'
import assert from 'node:assert/strict'
import { newReferralForm, referralPayload } from '../src/utils/referral-intake.mjs'
const form = phone => ({ ...newReferralForm(), name: '客户', phone })
test('empty or whitespace phone submits as null in both referral forms', () => {
  for (const phone of ['', '   ']) assert.equal(referralPayload(form(phone)).phone, null)
})
test('provided phone is validated and normalized', () => {
  assert.throws(() => referralPayload(form('123')), /手机号/)
  assert.equal(referralPayload(form(' 13800138000 ')).phone, '13800138000')
})
