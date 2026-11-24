#!/usr/bin/env python3
import json
import os
import time
import urllib.error
import urllib.request

TEMPLATE = "Linux by Zabbix agent active"
GROUP = "Linux servers"


class ZabbixApi:
    def __init__(self, url):
        self.url = url
        self.request_id = 0

    def call(self, method, params, token=None):
        self.request_id += 1
        body = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
            "id": self.request_id,
        }
        if token:
            body["auth"] = token
        request = urllib.request.Request(
            self.url,
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json-rpc"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            result = json.loads(response.read())
        if "error" in result:
            error = result["error"]
            raise RuntimeError(f"Zabbix {method}: {error.get('data', error)}")
        return result["result"]


def configure(api):
    token = api.call("user.login", {
        "username": os.environ["ZABBIX_USER"],
        "password": os.environ["ZABBIX_PASSWORD"],
    })
    hostname = os.environ["ZABBIX_HOST"]

    groups = api.call("hostgroup.get", {
        "output": ["groupid"], "filter": {"name": [GROUP]},
    }, token)
    templates = api.call("template.get", {
        "output": ["templateid"], "filter": {"host": [TEMPLATE]},
    }, token)
    if len(groups) != 1 or len(templates) != 1:
        raise RuntimeError(f"Required Zabbix group/template not found: {GROUP}, {TEMPLATE}")

    group_id = groups[0]["groupid"]
    template_id = templates[0]["templateid"]
    hosts = api.call("host.get", {
        "output": ["hostid", "status"],
        "selectParentTemplates": ["templateid"],
        "filter": {"host": [hostname]},
    }, token)
    if not hosts:
        api.call("host.create", {
            "host": hostname,
            "groups": [{"groupid": group_id}],
            "templates": [{"templateid": template_id}],
            "interfaces": [],
            "status": 0,
        }, token)
    else:
        host = hosts[0]
        if host["status"] != "0" or template_id not in {
            item["templateid"] for item in host["parentTemplates"]
        }:
            api.call("host.update", {
                "hostid": host["hostid"],
                "templates": [{"templateid": template_id}],
                "status": 0,
            }, token)

    verified = api.call("host.get", {
        "output": ["hostid", "host", "status"],
        "selectParentTemplates": ["templateid"],
        "filter": {"host": [hostname]},
    }, token)
    if len(verified) != 1 or verified[0]["status"] != "0" or template_id not in {
        item["templateid"] for item in verified[0]["parentTemplates"]
    }:
        raise RuntimeError(f"Host {hostname} was not configured correctly")
    print(f"Zabbix host {hostname} is configured with {TEMPLATE}")


def main():
    api = ZabbixApi(os.environ["ZABBIX_URL"])
    deadline = time.monotonic() + 120
    last_error = None
    while time.monotonic() < deadline:
        try:
            configure(api)
            return
        except (OSError, RuntimeError, urllib.error.URLError) as error:
            last_error = error
            time.sleep(2)
    raise SystemExit(f"Zabbix initialization failed: {last_error}")


if __name__ == "__main__":
    main()
