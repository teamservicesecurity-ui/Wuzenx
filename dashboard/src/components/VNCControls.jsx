import { useState, useCallback } from 'react';

/**
 * HVNC remote control buttons — send touch/swipe/button events to the device.
 * Commands are sent via the REST API (queued for the device).
 */
export default function VNCControls({ deviceId, onCommand }) {
  const [sending, setSending] = useState(false);

  const send = useCallback(async (type, payload) => {
    if (!deviceId || sending) return;
    setSending(true);
    try {
      await onCommand(type, payload);
    } finally {
      setTimeout(() => setSending(false), 200);
    }
  }, [deviceId, sending, onCommand]);

  const buttons = [
    { label: '⌂ Home', action: 'home' },
    { label: '◁ Back', action: 'back' },
    { label: '□ Recent', action: 'recent' },
    { label: '🔊 Vol+', action: 'volume_up' },
    { label: '🔉 Vol-', action: 'volume_down' },
    { label: '⏻ Power', action: 'power' },
  ];

  return (
    <div className="space-y-3">
      {/* Hardware buttons */}
      <div>
        <p className="text-[10px] text-surface-500 uppercase tracking-wider mb-2 font-mono">
          Hardware Controls
        </p>
        <div className="grid grid-cols-3 gap-1.5">
          {buttons.map(btn => (
            <button
              key={btn.action}
              onClick={() => send('hardware', { key: btn.action })}
              disabled={!deviceId || sending}
              className="h-8 text-xs bg-surface-800 hover:bg-surface-700 text-gray-200 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              {btn.label}
            </button>
          ))}
        </div>
      </div>

      {/* Touch injection */}
      <div>
        <p className="text-[10px] text-surface-500 uppercase tracking-wider mb-2 font-mono">
          Touch Mode
        </p>
        <div className="grid grid-cols-2 gap-1.5">
          <button
            onClick={() => send('touch', { mode: 'tap' })}
            disabled={!deviceId || sending}
            className="h-8 text-xs bg-accent/10 hover:bg-accent/20 text-accent rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            Tap
          </button>
          <button
            onClick={() => send('touch', { mode: 'swipe', direction: 'up' })}
            disabled={!deviceId || sending}
            className="h-8 text-xs bg-surface-800 hover:bg-surface-700 text-gray-200 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            Swipe ↑
          </button>
          <button
            onClick={() => send('touch', { mode: 'swipe', direction: 'down' })}
            disabled={!deviceId || sending}
            className="h-8 text-xs bg-surface-800 hover:bg-surface-700 text-gray-200 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            Swipe ↓
          </button>
          <button
            onClick={() => send('touch', { mode: 'long_press' })}
            disabled={!deviceId || sending}
            className="h-8 text-xs bg-surface-800 hover:bg-surface-700 text-gray-200 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            Long Press
          </button>
        </div>
      </div>

      {/* Text input */}
      <div>
        <p className="text-[10px] text-surface-500 uppercase tracking-wider mb-2 font-mono">
          Text Input
        </p>
        <TextInput deviceId={deviceId} onCommand={onCommand} sending={sending} />
      </div>
    </div>
  );
}

function TextInput({ deviceId, onCommand, sending }) {
  const [text, setText] = useState('');

  const handleSend = async () => {
    if (!text.trim()) return;
    await onCommand('text', { text: text });
    setText('');
  };

  return (
    <div className="flex gap-1.5">
      <input
        type="text"
        value={text}
        onChange={e => setText(e.target.value)}
        onKeyDown={e => e.key === 'Enter' && handleSend()}
        placeholder="Type text to send..."
        disabled={!deviceId}
        className="flex-1 h-8 px-2 text-xs bg-surface-800 border border-surface-700 rounded text-gray-200 placeholder-surface-500 disabled:opacity-30 outline-none focus:border-accent/50 transition-colors"
      />
      <button
        onClick={handleSend}
        disabled={!deviceId || !text.trim() || sending}
        className="h-8 px-3 text-xs bg-accent hover:bg-accent-dark text-white rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
      >
        Send
      </button>
    </div>
  );
}
