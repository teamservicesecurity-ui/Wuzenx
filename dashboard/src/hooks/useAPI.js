import { useState, useEffect, useCallback } from 'react';
import { useStore } from '../lib/store.js';

/**
 * Generic data-fetching hook.
 * Fetches from an API endpoint and manages loading/error states.
 */
export function useAPI(fetchFn, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFn();
      setData(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, deps); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    load();
  }, [load]);

  return { data, loading, error, refetch: load };
}

/**
 * Hook for dashboard polling — refetches data every N seconds.
 */
export function usePolling(fetchFn, interval = 15000, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const result = await fetchFn();
      setData(result);
    } catch {
      // Silent fail for polling
    } finally {
      setLoading(false);
    }
  }, deps); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    load();
    const timer = setInterval(load, interval);
    return () => clearInterval(timer);
  }, [load, interval]);

  return { data, loading };
}
