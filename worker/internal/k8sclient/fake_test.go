package k8sclient

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestFakeClient_ListNamespaces(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListNamespaces(context.Background())
	require.NoError(t, err)

	assert.Equal(t, 4, len(got), "should return 4 default k8s namespaces")

	// 验证 4 个真 ns 名都在
	names := make(map[string]bool)
	for _, ns := range got {
		names[ns.Name] = true
		assert.Equal(t, "Active", ns.Status)
		assert.NotEmpty(t, ns.Age)
	}
	assert.True(t, names["default"])
	assert.True(t, names["kube-system"])
	assert.True(t, names["kube-public"])
	assert.True(t, names["kube-node-lease"])
}

func TestFakeClient_ListPods_Shipyard(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListPods(context.Background(), "shipyard")
	require.NoError(t, err)

	assert.Equal(t, 3, len(got))
	// 验证 shipyard-backend / shipyard-web / shipyard-worker
	names := make(map[string]bool)
	for _, p := range got {
		names[p.Name] = true
		assert.Equal(t, "shipyard", p.Namespace)
		assert.Equal(t, "Running", p.Phase)
		assert.Equal(t, "1/1", p.Ready)
	}
	assert.True(t, names["shipyard-backend-7d4f8b6c9-x2k7m"])
	assert.True(t, names["shipyard-web-5b9c8d7f4-m8n3p"])
	assert.True(t, names["shipyard-worker-6c5d4b8f-a1b2c"])
}

func TestFakeClient_ListPods_KubeSystem(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListPods(context.Background(), "kube-system")
	require.NoError(t, err)
	assert.Equal(t, 2, len(got))
	// coredns + local-path-provisioner (k3d 默认)
}

func TestFakeClient_ListPods_Empty(t *testing.T) {
	c := NewFakeClient()
	for _, ns := range []string{"default", "kube-public", "kube-node-lease"} {
		got, err := c.ListPods(context.Background(), ns)
		require.NoError(t, err)
		assert.Equal(t, 0, len(got), "ns %s should have no pods in fake", ns)
	}
}

func TestFakeClient_ListPods_AllNamespaces(t *testing.T) {
	// 空 namespace → fake 默认返 shipyard 的
	c := NewFakeClient()
	got, err := c.ListPods(context.Background(), "")
	require.NoError(t, err)
	assert.Greater(t, len(got), 0, "empty namespace should fall back to shipyard")
}

func TestFakeClient_ListDeployments_Shipyard(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListDeployments(context.Background(), "shipyard")
	require.NoError(t, err)

	assert.Equal(t, 3, len(got))
	names := make(map[string]bool)
	for _, d := range got {
		names[d.Name] = true
		assert.Equal(t, "shipyard", d.Namespace)
		assert.Greater(t, d.Replicas, int32(0))
		assert.NotEmpty(t, d.Image)
	}
	assert.True(t, names["shipyard-backend"])
	assert.True(t, names["shipyard-web"])
	assert.True(t, names["shipyard-worker"])
}

func TestFakeClient_ListDeployments_KubeSystem(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListDeployments(context.Background(), "kube-system")
	require.NoError(t, err)
	assert.Equal(t, 2, len(got))
}

func TestFakeClient_ListDeployments_Empty(t *testing.T) {
	c := NewFakeClient()
	got, err := c.ListDeployments(context.Background(), "default")
	require.NoError(t, err)
	assert.Equal(t, 0, len(got))
}

func TestFakeClient_ClusterInfo(t *testing.T) {
	c := NewFakeClient()
	ver, node, err := c.ClusterInfo(context.Background())
	require.NoError(t, err)
	assert.Contains(t, ver, "v1.30", "version should look like v1.30.x-fake")
	assert.Contains(t, node, "k3d", "node name should be k3d-shipyard-server-0")
}

func TestFakeClient_FormatAge(t *testing.T) {
	c := NewFakeClient()
	assert.Equal(t, "30s", c.formatAge(30_000_000_000))      // 30s
	assert.Equal(t, "5m", c.formatAge(5*60_000_000_000))      // 5min
	assert.Equal(t, "2h", c.formatAge(2*3600*1e9))            // 2h
	assert.Equal(t, "3d", c.formatAge(3*24*3600*1e9))         // 3d
}

func TestIsInCluster_NotInCluster(t *testing.T) {
	// Mac 本机没 /var/run/secrets/.../token → 返 false
	assert.False(t, isInCluster())
}
