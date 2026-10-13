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

// ---------- 名单存取（JSON 文件，map: 名字 -> 信息）----------
get_roster() -> (
    data = read_file('bots_roster', 'json');
    if (data == null || type(data) != 'map',
        return({})
    );
    data
);

save_roster(m) -> (
    write_file('bots_roster', 'json', m);
);

mkinfo(p) -> (
    {
        'pos' -> [p~'x', p~'y', p~'z'],
        'dim' -> str(p~'dimension'),
        'yaw' -> p~'yaw',
        'pitch' -> p~'pitch'
    }
);

spawn_from_info(name, i) -> (
    p = i:'pos';
    run(str('player %s spawn at %f %f %f facing %f %f in %s',
        name, p:0, p:1, p:2, i:'yaw', i:'pitch', i:'dim'));
);

// ---------- 快照：在线假人更新条目，离线假人保留条目 ----------
do_snapshot() -> (
    r = get_roster();
    n = 0;
    for (player('all'),
        if (query(_, 'player_type') == 'fake',
            put(r, _~'name', mkinfo(_));
            n += 1;
        );
    );
    save_roster(r);
    n
);

// ---------- 启动时召回 ----------
__on_server_starts() -> (
    // 等 5 秒让世界加载完再召回
    schedule(100, __() -> (
        r = get_roster();
        for (keys(r),
            name = _;
            if (player(name) == null,
                spawn_from_info(name, r:name);
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
    ks = keys(r);
    if (length(ks) == 0,
        print('常驻名单为空'),
        for (ks,
            i = r:_;
            p = i:'pos';
            print(str(' - %s @ [%.1f, %.1f, %.1f] %s', _, p:0, p:1, p:2, i:'dim'));
        );
    );
);

remove(name) -> (
    r = get_roster();
    if (has(r, name),
        delete(r, name);
        save_roster(r);
        print('已将 ' + name + ' 移出常驻名单');
    ,
        print('名单里没有 ' + name);
    );
);
