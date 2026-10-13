// bots.sc — 假人常驻系统
// 自动记录所有 Carpet 假人的位置/维度/朝向，服务器重启后自动召回
// 用法:
//   /script in bots run list()          查看常驻假人名单
//   /script in bots run remove('名字')  把假人移出名单（不再召回）
//   /script in bots run snapshot()      手动立即快照一次

__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true
};

global_tick_count = 0;

// ---------- 名单存取（NBT 列表，元素为 {name, pos, dim, yaw, pitch}）----------
get_roster() -> (
    d = load_app_data();
    if (d == null, return([]));
    r = d:'roster';
    if (r == null, return([]));
    r
);

save_roster(list_of_entries) -> (
    d = load_app_data();
    if (d == null, d = nbt('{}'));
    put(d, 'roster', encode_nbt(list_of_entries));
    store_app_data(d);
);

mkinfo(p) -> (
    i = nbt('{}');
    put(i, 'name', encode_nbt(p~'name'));
    put(i, 'pos', encode_nbt([p~'x', p~'y', p~'z']));
    put(i, 'dim', encode_nbt(str(p~'dimension')));
    put(i, 'yaw', encode_nbt(p~'yaw'));
    put(i, 'pitch', encode_nbt(p~'pitch'));
    i
);

spawn_from_info(i) -> (
    p = i:'pos';
    run(str('player %s spawn at %f %f %f facing %f %f in %s',
        i:'name', p:0, p:1, p:2, i:'yaw', i:'pitch', i:'dim'));
);

// ---------- 快照：在线假人更新条目，离线假人保留条目 ----------
do_snapshot() -> (
    old = get_roster();
    // 收集当前在线假人
    online = {};
    for (player('all'),
        if (query(_, 'player_type') == 'fake',
            put(online, _~'name', mkinfo(_));
        );
    );
    // 合并：离线旧条目保留，在线条目更新
    out = [];
    for (old,
        nm = _:'name';
        if (!has(online, nm),
            out += _;
        );
    );
    for (keys(online),
        out += online:_;
    );
    save_roster(out);
    length(online)
);

// ---------- 启动时召回 ----------
__on_server_starts() -> (
    // 等 5 秒让世界加载完再召回
    schedule(100, __() -> (
        for (get_roster(),
            e = _;
            nm = e:'name';
            if (player(nm) == null,
                spawn_from_info(e);
            );
        );
    ));
);

// ---------- 每 60 秒自动快照 ----------
__on_tick() -> (
    global_tick_count += 1;
    if (global_tick_count % 1200 == 0,
        do_snapshot();
    );
);

// 关服前最后快照一次
__on_server_shuts_down() -> (
    do_snapshot();
);

// ---------- 指令接口 ----------
snapshot() -> (
    n = do_snapshot();
    print('已快照 ' + n + ' 个假人');
);

list() -> (
    r = get_roster();
    if (length(r) == 0,
        print('常驻名单为空'),
        for (r,
            p = _:'pos';
            print(str(' - %s @ [%.1f, %.1f, %.1f] %s', _:'name', p:0, p:1, p:2, _:'dim'));
        );
    );
);

remove(name) -> (
    old = get_roster();
    out = [];
    found = false;
    for (old,
        if (_:'name' == name,
            found = true;
        ,
            out += _;
        );
    );
    if (found,
        save_roster(out);
        print('已将 ' + name + ' 移出常驻名单');
    ,
        print('名单里没有 ' + name);
    );
);
