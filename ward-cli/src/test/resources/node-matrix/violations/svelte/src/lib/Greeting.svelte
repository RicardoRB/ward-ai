<script lang="ts">
	import { greeting } from './greeting';

	let { name }: { name: string } = $props();
	const count: number = 'one';
</script>

<p>{greeting(name)} {count}</p>
