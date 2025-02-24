const routes = [
    { path: "/", component: httpVueLoader('components/annotate.vue') }
];

const router = new VueRouter({
    routes: routes
});

const app = new Vue({
    router: router,
    data: {

    },
    mounted: function() {

    },
    methods: {

    }
}).$mount("#app");
