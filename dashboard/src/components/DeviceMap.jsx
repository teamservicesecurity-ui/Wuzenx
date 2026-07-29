import { useEffect, useRef } from 'react';

/**
 * Simple geolocation map using Leaflet.
 * Shows device markers with online/offline status.
 */
export default function DeviceMap({ devices, height = '300px' }) {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersRef = useRef([]);

  useEffect(() => {
    // Dynamic import Leaflet (it needs window)
    let L = null;
    import('leaflet').then(mod => {
      L = mod.default;

      // Fix Leaflet default icon path issue with bundlers
      delete L.Icon.Default.prototype._getIconUrl;
      L.Icon.Default.mergeOptions({
        iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
        iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
        shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      });

      if (!mapInstanceRef.current && mapRef.current) {
        mapInstanceRef.current = L.map(mapRef.current, {
          center: [20, 0],
          zoom: 2,
          zoomControl: true,
          attributionControl: false,
        });

        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
          maxZoom: 19,
        }).addTo(mapInstanceRef.current);
      }

      // Update markers
      if (mapInstanceRef.current && devices && devices.length > 0) {
        // Remove old markers
        markersRef.current.forEach(m => mapInstanceRef.current.removeLayer(m));
        markersRef.current = [];

        const validDevices = devices.filter(d => d.latitude && d.longitude);
        if (validDevices.length === 0) return;

        for (const device of validDevices) {
          const lat = parseFloat(device.latitude);
          const lng = parseFloat(device.longitude);
          if (isNaN(lat) || isNaN(lng)) continue;

          const color = device.is_online ? '#22c55e' : '#6b7280';
          const icon = L.divIcon({
            className: 'custom-marker',
            html: `<div style="
              width:12px;height:12px;border-radius:50%;
              background:${color};
              box-shadow:0 0 8px ${color}40;
              border:2px solid #0a0a0f;
            "></div>`,
            iconSize: [12, 12],
            iconAnchor: [6, 6],
          });

          const marker = L.marker([lat, lng], { icon }).addTo(mapInstanceRef.current);
          marker.bindPopup(`
            <div style="color:#111;font-size:12px;font-family:sans-serif;min-width:150px;">
              <strong>${device.name || device.model || 'Unknown'}</strong><br/>
              ${device.manufacturer || ''} ${device.model || ''}<br/>
              ${device.city || ''} ${device.country || ''}<br/>
              <span style="color:${device.is_online ? '#22c55e' : '#6b7280'}">
                ${device.is_online ? '● Online' : '○ Offline'}
              </span>
            </div>
          `);

          markersRef.current.push(marker);
        }

        // Fit bounds if multiple devices
        if (markersRef.current.length > 1) {
          const group = L.featureGroup(markersRef.current);
          mapInstanceRef.current.fitBounds(group.getBounds().pad(0.1));
        } else if (markersRef.current.length === 1) {
          mapInstanceRef.current.setView(
            markersRef.current[0].getLatLng(),
            10
          );
        }
      }
    });

    return () => {
      markersRef.current.forEach(m => {
        if (mapInstanceRef.current) mapInstanceRef.current.removeLayer(m);
      });
      markersRef.current = [];
      // Don't destroy map on re-render — only on unmount
    };
  }, [devices]);

  return <div ref={mapRef} style={{ height, width: '100%', borderRadius: '8px' }} />;
}
