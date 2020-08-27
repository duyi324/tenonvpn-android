package com.vm.shadowsocks.tunnel.shadowsocks;

import android.util.Log;

import com.vm.shadowsocks.core.LocalVpnService;
import com.vm.shadowsocks.tunnel.Tunnel;
import com.vm.shadowsocks.ui.P2pLibManager;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;


public class ShadowsocksTunnel extends Tunnel {
    private boolean m_TunnelEstablished;
    public ICrypt encryptor = null;
    private String seckey = "";

    public ShadowsocksTunnel(ShadowsocksConfig config, Selector selector) throws Exception {
        super(config.ServerAddress, selector);
    }

    protected int ipToNum(String ip) {
        int num = 0;
        String[] sections = ip.split("\\.");
        int i = 3;
        for (String str : sections) {
            num += (Long.parseLong(str) << (i * 8));
            i--;
        }
        return num;
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        buffer.clear();
        // https://shadowsocks.org/en/spec/protocol.html
        buffer.put((byte) 0x03);//domain
        byte[] domainBytes = m_DestAddress.getHostName().getBytes();
        buffer.put((byte) domainBytes.length);//domain length;
        buffer.put(domainBytes);
        buffer.putShort((short) m_DestAddress.getPort());
        buffer.flip();
        byte[] header = new byte[buffer.limit()];
        buffer.get(header);

        if (!seckey.equals(P2pLibManager.getInstance().seckey)) {
            encryptor = CryptFactory.get(P2pLibManager.getInstance().choosed_method, P2pLibManager.getInstance().seckey);
            seckey = P2pLibManager.getInstance().seckey;
        }
        byte[] enc_data = encryptor.encrypt(header);

        buffer.clear();
        if (P2pLibManager.getInstance().use_smart_route) {
            String ex_route = P2pLibManager.getInstance().GetExRouteNode();
            if (!ex_route.isEmpty()) {
                String[] splits = ex_route.split(":");
                if (splits.length == 2) {
                    String ex_route_ip = splits[0];
                    int ex_route_port = Integer.parseInt(splits[1]);
                    buffer.putInt(ipToNum(ex_route_ip));
                    buffer.putShort((short)ex_route_port);
                }
            }
            String vpn_ip = P2pLibManager.getInstance().choosed_vpn_ip;
            int vpn_port = P2pLibManager.getInstance().choosed_vpn_port;
            buffer.putInt(ipToNum(vpn_ip));
            buffer.putShort((short)vpn_port);
        }

        buffer.put(P2pLibManager.getInstance().public_key.getBytes());
        byte[] choosed_method_bytes = P2pLibManager.getInstance().platfrom.getBytes();
        buffer.put((byte) choosed_method_bytes.length);
        buffer.put(choosed_method_bytes);
        buffer.put(enc_data);
        buffer.flip();

        if (write(buffer, true)) {
            m_TunnelEstablished = true;
            onTunnelEstablished();
        } else {
            m_TunnelEstablished = true;
            this.beginReceive();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return m_TunnelEstablished;
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        byte[] enc_data = encryptor.encrypt(bytes);
        buffer.clear();
        buffer.put(enc_data);
        buffer.flip();
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        if (bytes.length == 3) {
            String str = new String(bytes);
            if (str.equals("bwo") || str.equals("cni") || str.equals("oul")) {
                P2pLibManager.getInstance().now_status = str;
                LocalVpnService.IsRunning = false;
                Log.e("shadowsocks 109", str + " stop vpn server.");

                return;
            }
        }
        byte[] newbytes = encryptor.decrypt(bytes);
        buffer.clear();
        buffer.put(newbytes);
        buffer.flip();
    }

    @Override
    protected void onDispose() {
        encryptor = null;
    }
}