## Binder机制

圆通、申通相当于内核

虚拟空间、物理空间

AIDL  可以实现BInder机制



Client-》bindService->Service返回Ibinder->Client拿到binder后可以像同进程一样调用接口



数据从client->service

Proxy ：Client  发送数据

stub：Server 接收数据