temp = 0
humid = 0
err = 0
timeFromBoot = 0
lastReadTime = 0

dofile("conf.lua")

tmr.alarm(0, 2000, 1, function()
timeFromBoot = tmr.time()
t,h =  dofile("dht22.lua").read(4, true)
if t and h then
    temp = t
    humid = h
    err = 0    
else
    err = 1 
end
if err == 0 then
    lastReadTime = timeFromBoot
end
end)

srv=net.createServer(net.TCP)
srv:listen(80,function(conn)
conn:on("receive",function(conn,payload)
conn:send("<p>B="..timeFromBoot.." R="..lastReadTime.." E="..err.." T="..string.format("%.1f",temp).."C H="..string.format("%.1f",humid).."% </p>")
conn:close()
end)
end)

