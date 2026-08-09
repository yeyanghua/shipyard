<script setup lang="ts">
/**
 * 创建项目页 - 表单 + 校验.
 *
 * <p>M4 接真后端.
 */
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useProjectStore } from '@/stores/project';
import { ApiError, type RepoProvider, type ProjectType } from '@/api';

const router = useRouter();
const store = useProjectStore();

const form = reactive({
  name: '',
  displayName: '',
  repoProvider: 'gitlab' as RepoProvider,
  repoUrl: '',
  repoToken: '',
  defaultBranch: 'main',
  projectType: 'java_maven' as ProjectType,
  description: '',
  projectMetaRaw: '',
});

const submitting = ref(false);
const errorMsg = ref('');

const repoProviderOptions: RepoProvider[] = ['gitlab', 'gitee'];
const projectTypeOptions: ProjectType[] = [
  'java_maven', 'java_gradle', 'node_pnpm', 'python_poetry', 'other',
];

const nameRe = /^[a-z0-9-]+$/;

async function onSubmit() {
  errorMsg.value = '';
  if (!form.name || !nameRe.test(form.name)) {
    errorMsg.value = 'name 只能包含小写字母/数字/中划线';
    return;
  }
  if (!form.displayName) {
    errorMsg.value = 'displayName 不能为空';
    return;
  }
  if (!form.repoUrl) {
    errorMsg.value = 'repoUrl 不能为空';
    return;
  }
  if (!form.projectType) {
    errorMsg.value = 'projectType 必填';
    return;
  }

  let projectMeta: Record<string, unknown> | undefined;
  if (form.projectMetaRaw.trim()) {
    try {
      projectMeta = JSON.parse(form.projectMetaRaw);
    } catch (e) {
      errorMsg.value = `projectMeta JSON 解析失败: ${(e as Error).message}`;
      return;
    }
  }

  submitting.value = true;
  try {
    const created = await store.create({
      name: form.name,
      displayName: form.displayName,
      repoProvider: form.repoProvider,
      repoUrl: form.repoUrl,
      repoToken: form.repoToken || undefined,
      defaultBranch: form.defaultBranch || 'main',
      projectType: form.projectType,
      description: form.description || undefined,
      projectMeta,
    });
    router.push(`/projects/${created.id}`);
  } catch (e) {
    errorMsg.value = (e as ApiError).message;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="page">
    <h1 class="title">创建项目</h1>

    <form class="form" @submit.prevent="onSubmit">
      <div class="field">
        <label>Name *<span class="hint">(英文, 小写字母/数字/中划线)</span></label>
        <input v-model="form.name" placeholder="demo-java-app" maxlength="64" />
      </div>

      <div class="field">
        <label>显示名 *</label>
        <input v-model="form.displayName" placeholder="Demo Java App" maxlength="128" />
      </div>

      <div class="row">
        <div class="field">
          <label>仓库平台 *</label>
          <select v-model="form.repoProvider">
            <option v-for="o in repoProviderOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
        <div class="field">
          <label>项目类型 *</label>
          <select v-model="form.projectType">
            <option v-for="o in projectTypeOptions" :key="o" :value="o">{{ o }}</option>
          </select>
        </div>
      </div>

      <div class="field">
        <label>仓库 URL *</label>
        <input v-model="form.repoUrl" placeholder="https://gitlab.example.com/group/demo.git" />
      </div>

      <div class="field">
        <label>仓库访问 Token<span class="hint">(选填, 加密存储)</span></label>
        <input v-model="form.repoToken" type="password" placeholder="glpat-xxxxxxxx" />
      </div>

      <div class="field">
        <label>默认分支</label>
        <input v-model="form.defaultBranch" maxlength="64" />
      </div>

      <div class="field">
        <label>Project Meta<span class="hint">(JSON 字符串, 选填)</span></label>
        <textarea v-model="form.projectMetaRaw" rows="3" placeholder='{"javaVersion":"21","mainClass":"com.example.App","port":8080}'></textarea>
      </div>

      <div class="field">
        <label>描述</label>
        <textarea v-model="form.description" rows="2" maxlength="512"></textarea>
      </div>

      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>

      <div class="actions">
        <button type="button" @click="router.back()">取消</button>
        <button type="submit" class="btn-primary" :disabled="submitting">
          {{ submitting ? '创建中...' : '创建' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.page { max-width: 720px; }
.title { font-size: 22px; margin: 0 0 24px; }
.form { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; }
.field { margin-bottom: 16px; }
.field label { display: block; font-size: 13px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; }
.field .hint { font-weight: 400; color: var(--text-soft); margin-left: 8px; }
.field input, .field select, .field textarea {
  width: 100%; padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px;
  font-family: inherit; font-size: 14px; box-sizing: border-box;
}
.row { display: flex; gap: 16px; }
.row .field { flex: 1; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.actions button { padding: 6px 16px; border: 1px solid var(--border); background: #fff; border-radius: 4px; cursor: pointer; }
.btn-primary { background: var(--primary) !important; color: #fff !important; border-color: var(--primary) !important; }
.error { color: #dc2626; background: #fee2e2; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; }
</style>
