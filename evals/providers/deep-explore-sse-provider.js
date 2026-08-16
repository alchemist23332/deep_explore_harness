'use strict';

const { performance } = require('node:perf_hooks');

function parseBlock(block) {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  return data ? JSON.parse(data) : null;
}

module.exports = class DeepExploreSseProvider {
  constructor(options) {
    this.providerId = options.id || 'deep-explore-sse';
    this.config = options.config || {};
  }

  id() {
    return this.providerId;
  }

  async callApi(prompt, context) {
    const baseUrl = this.config.baseUrl
      || process.env.DEEP_EXPLORE_BASE_URL
      || 'http://127.0.0.1:8080';
    const timeoutMs = this.config.timeoutMs || 90000;
    const startedAt = performance.now();

    const headers = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    };
    if (context?.traceparent) {
      headers.traceparent = context.traceparent;
    }

    let response;
    try {
      response = await fetch(`${baseUrl}/api/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          conversationId: null,
          message: prompt,
          mode: this.config.mode || 'FAST',
          userMessageId: null,
          userParentMessageId: null,
          assistantMessageId: null,
        }),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (error) {
      return { error: `Deep Explore request failed: ${error.message}` };
    }

    if (!response.ok) {
      return {
        error: `Deep Explore returned HTTP ${response.status}: ${await response.text()}`,
      };
    }
    if (!response.body) {
      return { error: 'Deep Explore returned an empty SSE body' };
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let output = '';
    let conversationId;
    let firstDeltaAt;
    let eventCount = 0;
    let completed = false;

    try {
      while (true) {
        const { value, done } = await reader.read();
        buffer += decoder.decode(value, { stream: !done });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || '';

        for (const block of blocks) {
          const event = parseBlock(block);
          if (!event) continue;
          eventCount += 1;
          conversationId ||= event.conversationId;

          if (event.type === 'delta') {
            firstDeltaAt ||= performance.now();
            output += event.content;
          } else if (event.type === 'error') {
            return {
              error: event.content || 'Deep Explore returned an error event',
              metadata: { conversationId, eventCount },
            };
          } else if (event.type === 'done') {
            completed = true;
          }
        }

        if (done) break;
      }
    } catch (error) {
      return {
        error: `Unable to read Deep Explore SSE stream: ${error.message}`,
        metadata: { conversationId, eventCount },
      };
    }

    if (!completed) {
      return {
        error: 'Deep Explore SSE response ended without a done event',
        metadata: { conversationId, eventCount },
      };
    }

    const completedAt = performance.now();
    return {
      output,
      sessionId: conversationId,
      metadata: {
        conversationId,
        eventCount,
        ttftMs: firstDeltaAt ? Math.round(firstDeltaAt - startedAt) : null,
        endToEndLatencyMs: Math.round(completedAt - startedAt),
      },
    };
  }
};
