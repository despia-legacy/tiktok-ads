import despia from 'despia-native';

const subscriptions = new Map();

export async function extensionCall(extension, route, params = {}) {
  const suffix = crypto.randomUUID().replaceAll('-', '');
  const callback = `despiaNxCallback${suffix}`;
  const watched = `despiaNxResult${suffix}`;
  const query = new URLSearchParams();
  for (const [name, parameter] of Object.entries(params)) {
    if (parameter === undefined || parameter === null) continue;
    query.set(name, typeof parameter === 'object' ? JSON.stringify(parameter) : String(parameter));
  }
  query.set('callback', callback);
  globalThis[callback] = envelope => { globalThis[watched] = envelope; };
  try {
    const observed = await despia(`extension://${extension}/${route}?${query.toString().replace(/\+/g, '%20')}`, [watched]);
    const envelope = observed[watched];
    if (envelope === undefined) {
      throw Object.assign(new Error('The SDK did not observe a response within 30 seconds'), { code: 'sdk_timeout' });
    }
    if (!envelope.ok) {
      throw Object.assign(new Error(envelope.error || 'The native operation failed'), { code: envelope.code });
    }
    return envelope.data;
  } finally {
    delete globalThis[callback];
    delete globalThis[watched];
  }
}

export function onExtensionEvent(callback, listener) {
  let subscription = subscriptions.get(callback);
  if (!subscription) {
    const previous = globalThis[callback];
    const listeners = new Set();
    const receive = payload => {
      if (typeof previous === 'function') {
        try { previous(payload); }
        catch (error) { console.error('Existing extension event handler failed', error); }
      }
      for (const notify of [...listeners]) {
        try { notify(payload); }
        catch (error) { console.error('Extension event subscriber failed', error); }
      }
    };
    subscription = { previous, listeners, receive };
    subscriptions.set(callback, subscription);
    globalThis[callback] = receive;
  }
  const notify = payload => listener(payload);
  subscription.listeners.add(notify);
  let active = true;
  return () => {
    if (!active) return;
    active = false;
    subscription.listeners.delete(notify);
    if (subscription.listeners.size !== 0) return;
    if (globalThis[callback] === subscription.receive) {
      if (subscription.previous === undefined) delete globalThis[callback];
      else globalThis[callback] = subscription.previous;
    }
    subscriptions.delete(callback);
  };
}
