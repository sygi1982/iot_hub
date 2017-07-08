if ip == "192.168.1.1" then
    print("Got hub configuration!")
else
    print("Updating hub configuration!")

    cfg={}
    cfg.ssid="IOT_HUB_01"
    cfg.pwd="12345678"
    wifi.ap.config(cfg)
    
    cfg={}
    cfg.ip="192.168.1.1"
    cfg.netmask="255.255.255.0"
    cfg.gateway="192.168.1.1"
    wifi.ap.setip(cfg)
    wifi.setmode(wifi.SOFTAP)
end
